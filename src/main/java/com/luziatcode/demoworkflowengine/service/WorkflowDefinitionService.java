package com.luziatcode.demoworkflowengine.service;

import com.luziatcode.demoworkflowengine.service.workflow.domain.Edge;
import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import com.luziatcode.demoworkflowengine.service.workflow.domain.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowDefinitionService {
    private final WorkflowDefinitionRepository repository;

    public WorkflowDefinition save(WorkflowDefinition definition) {
        validate(definition);
        return repository.save(definition);
    }

    public WorkflowDefinition getLatest(String workflowId) {
        return repository.findLatestById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));
    }

    public WorkflowDefinition getRequired(String workflowId, int version) {
        return repository.findByIdAndVersion(workflowId, version)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId + " version " + version));
    }

    private void validate(WorkflowDefinition definition) {
        if (definition.getId() == null || definition.getId().isBlank()) {
            throw new IllegalArgumentException("Workflow id is required");
        }
        if (definition.getVersion() <= 0) {
            throw new IllegalArgumentException("Workflow version must be positive");
        }
        if (definition.getNodes().isEmpty()) {
            throw new IllegalArgumentException("At least one node is required");
        }
        Set<String> nodeIds = new HashSet<>();
        for (Node node : definition.getNodes()) {
            if (node.getNodeId() == null || node.getNodeId().isBlank()) {
                throw new IllegalArgumentException("Node id is required");
            }
            if (!nodeIds.add(node.getNodeId())) {
                throw new IllegalArgumentException("Duplicate node id: " + node.getNodeId());
            }
        }
        for (Edge edge : definition.getEdges()) {
            if (!nodeIds.contains(edge.getFrom()) || !nodeIds.contains(edge.getTo())) {
                throw new IllegalArgumentException("Edge references unknown node: " + edge.getFrom() + " -> " + edge.getTo());
            }
        }
        long startCount = definition.getNodes().stream().filter(node -> ActionType.START.equals(node.getActionType())).count();
        if (startCount != 1) {
            throw new IllegalArgumentException("Exactly one start node is required");
        }
    }
}
