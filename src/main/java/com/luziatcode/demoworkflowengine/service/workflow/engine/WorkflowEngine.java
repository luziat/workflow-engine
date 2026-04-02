package com.luziatcode.demoworkflowengine.service.workflow.engine;

import com.luziatcode.demoworkflowengine.service.workflow.domain.*;
import com.luziatcode.demoworkflowengine.service.workflow.action.Action;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class WorkflowEngine {
    private final ConcurrentMap<String, ExecutionBoundAction> activeActions = new ConcurrentHashMap<>();

    private final NodeExecutorRegistry registry;
    private final WorkflowExecutionService workflowExecutionService;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final SimpleConditionEvaluator conditionEvaluator;

    public WorkflowExecution run(WorkflowDefinition definition, WorkflowExecution workflowExecution) {
        NodeExecution nodeExecution = null;
        try {
            Node current = workflowExecution.getCurrentNodeId() == null
                    ? findStartNode(definition)
                    : findNode(definition, workflowExecution.getCurrentNodeId());

            workflowExecution.setStatus(ExecutionStatus.RUNNING);
            workflowExecutionService.update(workflowExecution);

            while (current != null) {
                workflowExecution.setCurrentNodeId(current.getNodeId());
                workflowExecutionService.update(workflowExecution);

                if (workflowExecution.getStatus() == ExecutionStatus.STOPPING) {
                    return stopExecution(workflowExecution, nodeExecution, "Stopped before node execution");
                }

                nodeExecution = buildNodeExecution(workflowExecution, current);
                Map<String, Object> contextBeforeExecution = new LinkedHashMap<>(workflowExecution.getContext());
                ExecutionBoundAction action = new ExecutionBoundAction(registry.getRequired(current.getActionType()));
                activeActions.put(workflowExecution.getExecutionId(), action);
                try {
                    action.execute(new NodeExecutionContext(definition, workflowExecution, current));
                } finally {
                    activeActions.remove(workflowExecution.getExecutionId(), action);
                }

                nodeExecution.setOutput(extractNodeOutput(contextBeforeExecution, workflowExecution.getContext()));
                nodeExecution.setEndedAt(Instant.now());

                if (workflowExecution.getStatus() == ExecutionStatus.STOPPING) {
                    return stopExecution(workflowExecution, nodeExecution, action.stopReason());
                }

                Node next = resolveNextNode(definition, current, workflowExecution.getContext());
                nodeExecution.setStatus(ExecutionStatus.SUCCESS);
                nodeExecutionRepository.save(nodeExecution);
                nodeExecution = null;
                current = next;
            }

            workflowExecution.setStatus(ExecutionStatus.SUCCESS);
            workflowExecution.setCurrentNodeId(null);
            return workflowExecutionService.update(workflowExecution);
        } catch (Exception exception) {
            if (nodeExecution != null) {
                nodeExecution.setStatus(ExecutionStatus.FAILED);
                nodeExecution.setEndedAt(Instant.now());
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

        ExecutionBoundAction activeAction = activeActions.get(executionId);
        if (activeAction == null) {
            return stopExecution(execution, null, reason);
        }

        activeAction.stop(reason);
        return workflowExecutionService.getRequired(executionId);
    }

    private NodeExecution buildNodeExecution(WorkflowExecution execution, Node current) {
        NodeExecution nodeExecution = new NodeExecution();
        nodeExecution.setExecutionId(execution.getExecutionId());
        nodeExecution.setNodeId(current.getNodeId());
        nodeExecution.setStatus(ExecutionStatus.RUNNING);
        nodeExecution.setStartedAt(Instant.now());
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
            nodeExecution.setEndedAt(nodeExecution.getEndedAt() != null ? nodeExecution.getEndedAt() : Instant.now());
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
                .filter(node -> ActionType.START.equals(node.getActionType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Start node not found"));
    }

    private Node findNode(WorkflowDefinition definition, String nodeId) {
        return definition.getNodes().stream()
                .filter(node -> node.getNodeId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
    }

    private Node resolveNextNode(WorkflowDefinition definition, Node current, Map<String, Object> context) {
        List<Edge> outgoingEdges = definition.getEdges().stream()
                .filter(edge -> edge.getFrom().equals(current.getNodeId()))
                .toList();

        if (outgoingEdges.isEmpty()) {
            return null;
        }

        List<Edge> conditionalEdges = outgoingEdges.stream()
                .filter(edge -> edge.getCondition() != null && !edge.getCondition().isBlank())
                .toList();
        List<Edge> defaultEdges = outgoingEdges.stream()
                .filter(edge -> edge.getCondition() == null || edge.getCondition().isBlank())
                .toList();

        List<Edge> matchedConditionalEdges = conditionalEdges.stream()
                .filter(edge -> conditionEvaluator.matches(edge.getCondition(), context))
                .toList();

        if (matchedConditionalEdges.size() > 1) {
            throw new IllegalStateException("Multiple edges matched from node: " + current.getNodeId());
        }
        if (matchedConditionalEdges.size() == 1) {
            return findNode(definition, matchedConditionalEdges.getFirst().getTo());
        }
        if (defaultEdges.isEmpty()) {
            return null;
        }
        if (defaultEdges.size() > 1) {
            throw new IllegalStateException("Multiple default edges configured from node: " + current.getNodeId());
        }
        return findNode(definition, defaultEdges.getFirst().getTo());
    }

    private static final class ExecutionBoundAction implements Action {
        private final Action delegate;
        private final AtomicReference<Thread> executingThread = new AtomicReference<>();
        private final AtomicReference<String> stopReason = new AtomicReference<>("Stopped by user request");

        private ExecutionBoundAction(Action delegate) {
            this.delegate = delegate;
        }

        @Override
        public ActionType getType() {
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
}
