package com.luziatcode.demoworkflowengine.engine;

import com.luziatcode.demoworkflowengine.domain.Edge;
import com.luziatcode.demoworkflowengine.domain.ExecutionStatus;
import com.luziatcode.demoworkflowengine.domain.Node;
import com.luziatcode.demoworkflowengine.domain.NodeExecution;
import com.luziatcode.demoworkflowengine.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.domain.WorkflowExecution;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.service.WorkflowExecutionService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowEngine {
    private final NodeExecutorRegistry registry;
    private final WorkflowExecutionService workflowExecutionService;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final SimpleConditionEvaluator conditionEvaluator;

    public WorkflowEngine(NodeExecutorRegistry registry,
                          WorkflowExecutionService workflowExecutionService,
                          NodeExecutionRepository nodeExecutionRepository,
                          SimpleConditionEvaluator conditionEvaluator) {
        this.registry = registry;
        this.workflowExecutionService = workflowExecutionService;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.conditionEvaluator = conditionEvaluator;
    }

    public WorkflowExecution run(WorkflowDefinition definition, WorkflowExecution execution) {
        Node current = execution.getCurrentNodeId() == null
                ? findStartNode(definition)
                : findNode(definition, execution.getCurrentNodeId());

        execution.setStatus(ExecutionStatus.RUNNING);
        workflowExecutionService.update(execution);

        while (current != null) {
            execution.setCurrentNodeId(current.getId());
            workflowExecutionService.update(execution);

            NodeExecution nodeExecution = beginNodeExecution(execution, current);
            NodeResult result;
            try {
                result = registry.getRequired(current.getType())
                        .execute(new NodeExecutionContext(definition, execution, current));
            } catch (Exception exception) {
                nodeExecution.setStatus(ExecutionStatus.FAILED);
                nodeExecution.setEndedAt(Instant.now());
                nodeExecution.setMessage(exception.getMessage());
                nodeExecutionRepository.save(nodeExecution);

                execution.setStatus(ExecutionStatus.FAILED);
                execution.setFailureMessage(exception.getMessage());
                return workflowExecutionService.update(execution);
            }

            execution.getContext().putAll(result.getOutput());
            nodeExecution.setOutput(result.getOutput());
            nodeExecution.setEndedAt(Instant.now());

            if (result.getDirective() == ExecutionDirective.WAIT) {
                nodeExecution.setStatus(ExecutionStatus.WAITING);
                nodeExecution.setMessage(result.getMessage());
                nodeExecutionRepository.save(nodeExecution);

                execution.setStatus(ExecutionStatus.WAITING);
                execution.setWaitingNodeId(current.getId());
                return workflowExecutionService.update(execution);
            }

            nodeExecution.setStatus(ExecutionStatus.SUCCESS);
            nodeExecutionRepository.save(nodeExecution);

            if (result.getDirective() == ExecutionDirective.FINISH) {
                execution.setStatus(ExecutionStatus.SUCCESS);
                execution.setCurrentNodeId(null);
                execution.setWaitingNodeId(null);
                return workflowExecutionService.update(execution);
            }

            current = resolveNextNode(definition, current, execution.getContext());
        }

        execution.setStatus(ExecutionStatus.SUCCESS);
        execution.setCurrentNodeId(null);
        execution.setWaitingNodeId(null);
        return workflowExecutionService.update(execution);
    }

    private NodeExecution beginNodeExecution(WorkflowExecution execution, Node current) {
        NodeExecution nodeExecution = new NodeExecution();
        nodeExecution.setExecutionId(execution.getExecutionId());
        nodeExecution.setNodeId(current.getId());
        nodeExecution.setStatus(ExecutionStatus.RUNNING);
        nodeExecution.setStartedAt(Instant.now());
        nodeExecution.setInput(Map.copyOf(execution.getContext()));
        return nodeExecution;
    }

    private Node findStartNode(WorkflowDefinition definition) {
        return definition.getNodes().stream()
                .filter(node -> "start".equals(node.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Start node not found"));
    }

    private Node findNode(WorkflowDefinition definition, String nodeId) {
        return definition.getNodes().stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
    }

    private Node resolveNextNode(WorkflowDefinition definition, Node current, Map<String, Object> context) {
        List<Edge> outgoingEdges = definition.getEdges().stream()
                .filter(edge -> edge.getFrom().equals(current.getId()))
                .sorted(Comparator.comparing(edge -> edge.getCondition() == null ? 1 : 0))
                .toList();

        if (outgoingEdges.isEmpty()) {
            return null;
        }

        List<Edge> candidates = new ArrayList<>();
        for (Edge edge : outgoingEdges) {
            if (conditionEvaluator.matches(edge.getCondition(), context)) {
                candidates.add(edge);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException("Multiple edges matched from node: " + current.getId());
        }
        return findNode(definition, candidates.getFirst().getTo());
    }
}
