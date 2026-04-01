package com.luziatcode.demoworkflowengine.service.workflow.engine;

import com.luziatcode.demoworkflowengine.service.workflow.domain.*;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkflowEngine {
    private final NodeExecutorRegistry registry;
    private final WorkflowExecutionService workflowExecutionService;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final SimpleConditionEvaluator conditionEvaluator;

    public WorkflowExecution run(WorkflowDefinition definition, WorkflowExecution execution) {
        NodeExecution nodeExecution = null;
        try {
            Node current = execution.getCurrentNodeId() == null
                    ? findStartNode(definition)
                    : findNode(definition, execution.getCurrentNodeId());

            execution.setStatus(ExecutionStatus.RUNNING);
            workflowExecutionService.update(execution);

            while (current != null) {
                execution.setCurrentNodeId(current.getNodeId());
                workflowExecutionService.update(execution);

                nodeExecution = beginNodeExecution(execution, current);
                Map<String, Object> contextBeforeExecution = new LinkedHashMap<>(execution.getContext());
                registry.getRequired(current.getActionType())
                        .execute(new NodeExecutionContext(definition, execution, current));

                nodeExecution.setOutput(extractNodeOutput(contextBeforeExecution, execution.getContext()));
                nodeExecution.setEndedAt(Instant.now());

                Node next = resolveNextNode(definition, current, execution.getContext());
                nodeExecution.setStatus(ExecutionStatus.SUCCESS);
                nodeExecutionRepository.save(nodeExecution);
                nodeExecution = null;
                current = next;
            }

            execution.setStatus(ExecutionStatus.SUCCESS);
            execution.setCurrentNodeId(null);
            return workflowExecutionService.update(execution);
        } catch (Exception exception) {
            if (nodeExecution != null) {
                nodeExecution.setStatus(ExecutionStatus.FAILED);
                nodeExecution.setEndedAt(Instant.now());
                nodeExecution.setMessage(exception.getMessage());
                nodeExecutionRepository.save(nodeExecution);
            }

            execution.setStatus(ExecutionStatus.FAILED);
            execution.setFailureMessage(exception.getMessage());
            return workflowExecutionService.update(execution);
        }
    }

    private NodeExecution beginNodeExecution(WorkflowExecution execution, Node current) {
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
}
