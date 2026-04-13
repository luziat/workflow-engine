package com.luziatcode.demoworkflowengine.service.workflow.engine;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnectionTarget;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.NodeExecution;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class WorkflowEngine {
    private static final int MAX_NODE_VISITS = 1_000;

    private final ConcurrentMap<String, ExecutionBoundNodeExecutor> activeExecutors = new ConcurrentHashMap<>();

    private final NodeExecutorRegistry registry;
    private final WorkflowExecutionService workflowExecutionService;
    private final NodeExecutionRepository nodeExecutionRepository;

    public WorkflowExecution run(WorkflowExecution workflowExecution) {
        WorkflowDefinition definition = workflowExecution.getDefinition();
        if (definition == null) {
            throw new IllegalArgumentException("Workflow definition not found in execution");
        }
        NodeExecution nodeExecution = null;
        try {
            ArrayDeque<String> readyNodes = new ArrayDeque<>();
            Map<String, Node> nodesById = indexNodesById(definition);
            Map<String, Integer> mergeInputCounts = countMergeInputs(definition, nodesById);
            Map<String, Set<Integer>> mergeArrivals = new HashMap<>();
            int visitedCount = 0;

            Node startNode = workflowExecution.getCurrentNodeId() == null
                    ? findStartNode(definition)
                    : findNode(definition, workflowExecution.getCurrentNodeId());
            readyNodes.add(startNode.getId());

            workflowExecution.setStatus(ExecutionStatus.RUNNING);
            workflowExecutionService.update(workflowExecution);

            while (!readyNodes.isEmpty()) {
                if (visitedCount++ >= MAX_NODE_VISITS) {
                    throw new IllegalStateException("Workflow exceeded maximum node visits: " + MAX_NODE_VISITS);
                }

                Node current = nodesById.get(readyNodes.removeFirst());
                if (current == null) {
                    continue;
                }

                workflowExecution.setCurrentNodeId(current.getId());
                workflowExecutionService.update(workflowExecution);

                if (workflowExecution.getStatus() == ExecutionStatus.STOPPING) {
                    return stopExecution(workflowExecution, nodeExecution, "Stopped before node execution");
                }

                nodeExecution = buildNodeExecution(workflowExecution, current);
                Map<String, Object> contextBeforeExecution = new LinkedHashMap<>(workflowExecution.getContext());
                List<Integer> selectedOutputs;
                if (current.isDisabled()) {
                    selectedOutputs = connectedOutputs(definition, current);
                } else {
                    ExecutionBoundNodeExecutor executor = new ExecutionBoundNodeExecutor(registry.getRequired(current.getType()));
                    activeExecutors.put(workflowExecution.getExecutionId(), executor);
                    try {
                        executor.execute(new NodeExecutionContext(definition, workflowExecution, current));
                        selectedOutputs = executor.selectOutputs(new NodeExecutionContext(definition, workflowExecution, current));
                    } finally {
                        activeExecutors.remove(workflowExecution.getExecutionId(), executor);
                    }

                    nodeExecution.setOutput(extractNodeOutput(contextBeforeExecution, workflowExecution.getContext()));
                    nodeExecution.setEndedAt(ZonedDateTime.now());

                    if (workflowExecution.getStatus() == ExecutionStatus.STOPPING) {
                        return stopExecution(workflowExecution, nodeExecution, executor.stopReason());
                    }
                }
                if (current.isDisabled()) {
                    nodeExecution.setOutput(extractNodeOutput(contextBeforeExecution, workflowExecution.getContext()));
                    nodeExecution.setEndedAt(ZonedDateTime.now());
                }

                nodeExecution.setStatus(ExecutionStatus.SUCCESS);
                nodeExecutionRepository.save(nodeExecution);
                nodeExecution = null;

                for (OutgoingConnection outgoing : resolveOutgoing(definition, current, nodesById, selectedOutputs)) {
                    Node target = outgoing.target();
                    if (NodeType.MERGE.equals(target.getType())) {
                        Set<Integer> arrivals = mergeArrivals.computeIfAbsent(target.getId(), key -> new HashSet<>());
                        arrivals.add(outgoing.targetInputIndex());
                        if (arrivals.size() < mergeInputCounts.getOrDefault(target.getId(), 1)) {
                            continue;
                        }
                        arrivals.clear();
                    }
                    readyNodes.addLast(target.getId());
                }
            }

            workflowExecution.setStatus(ExecutionStatus.SUCCESS);
            workflowExecution.setCurrentNodeId(null);
            return workflowExecutionService.update(workflowExecution);
        } catch (Exception exception) {
            if (nodeExecution != null) {
                nodeExecution.setStatus(ExecutionStatus.FAILED);
                nodeExecution.setEndedAt(ZonedDateTime.now());
                nodeExecution.setMessage(exception.getMessage());
                nodeExecutionRepository.save(nodeExecution);
            }

            workflowExecution.setStatus(ExecutionStatus.FAILED);
            workflowExecution.setFailureMessage(exception.getMessage());
            return workflowExecutionService.update(workflowExecution);
        }
    }

    public WorkflowExecution stop(String executionId, String reason) {
        WorkflowExecution execution = workflowExecutionService.markStopping(executionId);
        if (execution.getStatus() != ExecutionStatus.STOPPING) {
            return execution;
        }

        ExecutionBoundNodeExecutor activeExecutor = activeExecutors.get(executionId);
        if (activeExecutor == null) {
            return stopExecution(execution, null, reason);
        }

        activeExecutor.stop(reason);
        return workflowExecutionService.getRequired(executionId);
    }

    private NodeExecution buildNodeExecution(WorkflowExecution execution, Node current) {
        NodeExecution nodeExecution = new NodeExecution();
        nodeExecution.setExecutionId(execution.getExecutionId());
        nodeExecution.setNodeId(current.getId());
        nodeExecution.setStatus(ExecutionStatus.RUNNING);
        nodeExecution.setStartedAt(ZonedDateTime.now());
        nodeExecution.setInput(Map.copyOf(execution.getContext()));
        return nodeExecution;
    }

    private Map<String, Object> extractNodeOutput(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
                output.put(entry.getKey(), entry.getValue());
            }
        }
        return output;
    }

    private WorkflowExecution stopExecution(WorkflowExecution workflowExecution, NodeExecution nodeExecution, String reason) {
        if (nodeExecution != null) {
            nodeExecution.setStatus(ExecutionStatus.STOPPED);
            nodeExecution.setEndedAt(nodeExecution.getEndedAt() != null ? nodeExecution.getEndedAt() : ZonedDateTime.now());
            nodeExecution.setMessage(reason);
            nodeExecutionRepository.save(nodeExecution);
        }

        workflowExecution.setStatus(ExecutionStatus.STOPPED);
        workflowExecution.setCurrentNodeId(null);
        workflowExecution.setFailureMessage(reason);
        return workflowExecutionService.update(workflowExecution);
    }

    private Node findStartNode(WorkflowDefinition definition) {
        return definition.getNodes().stream()
                .filter(node -> NodeType.START.equals(node.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Start node not found"));
    }

    private Node findNode(WorkflowDefinition definition, String nodeId) {
        return definition.getNodes().stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
    }

    private Map<String, Node> indexNodesById(WorkflowDefinition definition) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Node node : definition.getNodes()) {
            nodes.put(node.getId(), node);
        }
        return nodes;
    }

    private Map<String, Integer> countMergeInputs(WorkflowDefinition definition, Map<String, Node> nodesById) {
        Map<String, Integer> inputCounts = new HashMap<>();
        definition.getConnections().values().stream()
                .map(connections -> connections.getMain())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .forEach(target -> {
                    Node node = nodesById.get(target.getNode());
                    if (node != null && NodeType.MERGE.equals(node.getType())) {
                        inputCounts.merge(node.getId(), 1, Integer::sum);
                    }
                });
        return inputCounts;
    }

    private List<Integer> connectedOutputs(WorkflowDefinition definition, Node node) {
        List<Integer> outputs = new ArrayList<>();
        var nodeConnections = definition.getConnections().get(node.getId());
        if (nodeConnections == null || nodeConnections.getMain() == null) {
            return outputs;
        }
        for (int index = 0; index < nodeConnections.getMain().size(); index++) {
            List<?> targets = nodeConnections.getMain().get(index);
            if (targets != null && !targets.isEmpty()) {
                outputs.add(index);
            }
        }
        return outputs;
    }

    private List<OutgoingConnection> resolveOutgoing(WorkflowDefinition definition,
                                                     Node current,
                                                     Map<String, Node> nodesById,
                                                     List<Integer> selectedOutputs) {
        List<OutgoingConnection> outgoing = new ArrayList<>();
        var nodeConnections = definition.getConnections().get(current.getId());
        if (nodeConnections == null || nodeConnections.getMain() == null) {
            return outgoing;
        }
        for (Integer outputIndex : selectedOutputs) {
            if (outputIndex < 0 || outputIndex >= nodeConnections.getMain().size()) {
                continue;
            }
            List<NodeConnectionTarget> targets =
                    nodeConnections.getMain().get(outputIndex);
            if (targets == null) {
                continue;
            }
            for (var target : targets) {
                Node targetNode = nodesById.get(target.getNode());
                if (targetNode != null) {
                    outgoing.add(new OutgoingConnection(targetNode, target.getIndex()));
                }
            }
        }
        return outgoing;
    }

    private static final class ExecutionBoundNodeExecutor implements NodeExecutor {
        private final NodeExecutor delegate;
        private final AtomicReference<Thread> executingThread = new AtomicReference<>();
        private final AtomicReference<String> stopReason = new AtomicReference<>("Stopped by user request");

        private ExecutionBoundNodeExecutor(NodeExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public NodeType getType() {
            return delegate.getType();
        }

        @Override
        public void execute(NodeExecutionContext context) {
            executingThread.set(Thread.currentThread());
            try {
                delegate.execute(context);
            } finally {
                executingThread.set(null);
            }
        }

        @Override
        public List<Integer> selectOutputs(NodeExecutionContext context) {
            return delegate.selectOutputs(context);
        }

        @Override
        public void stop(String reason) {
            stopReason.set(reason);
            delegate.stop(reason);

            Thread thread = executingThread.get();
            if (thread != null) {
                thread.interrupt();
            }
        }

        private String stopReason() {
            return stopReason.get();
        }
    }

    private record OutgoingConnection(Node target, int targetInputIndex) {
    }
}
