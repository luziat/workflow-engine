package com.luziatcode.demoworkflowengine.service.workflow;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnectionTarget;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnections;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutorRegistry;
import com.luziatcode.demoworkflowengine.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowDefinitionService {
    private final WorkflowDefinitionRepository repository;
    private final NodeExecutorRegistry nodeExecutorRegistry;

    public WorkflowDefinition save(WorkflowDefinition definition) {
        if (definition.getVersion() <= 0) {
            definition.setVersion(1);
        }
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

    public List<WorkflowDefinition> getAllVersions(String workflowId) {
        List<WorkflowDefinition> definitions = repository.findAllById(workflowId);
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        return definitions;
    }

    public List<WorkflowDefinition> getAllLatest() {
        return repository.findAllLatest();
    }

    public void delete(String workflowId) {
        if (!repository.deleteAllById(workflowId)) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
    }

    public void delete(String workflowId, int version) {
        if (!repository.deleteByIdAndVersion(workflowId, version)) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId + " version " + version);
        }
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
            if (node.getId() == null || node.getId().isBlank()) {
                throw new IllegalArgumentException("Node id is required");
            }
            if (!nodeIds.add(node.getId())) {
                throw new IllegalArgumentException("Duplicate node id: " + node.getId());
            }
            if (node.getName() == null || node.getName().isBlank()) {
                throw new IllegalArgumentException("Node name is required");
            }
            if (node.getType() == null) {
                throw new IllegalArgumentException("Node type is required: " + node.getId());
            }
            if (!nodeExecutorRegistry.supportedTypes().contains(node.getType())) {
                throw new IllegalArgumentException("Unsupported node type: " + node.getType());
            }
        }
        for (var entry : definition.getConnections().entrySet()) {
            if (!nodeIds.contains(entry.getKey())) {
                throw new IllegalArgumentException("Connections reference unknown source node: " + entry.getKey());
            }
            validateConnections(nodeIds, entry.getValue());
        }
        long startCount = definition.getNodes().stream().filter(node -> NodeType.START.equals(node.getType())).count();
        if (startCount != 1) {
            throw new IllegalArgumentException("Exactly one start node is required");
        }
        validateExistingWorkflowId(definition);
    }

    private void validateExistingWorkflowId(WorkflowDefinition definition) {
        repository.findLatestById(definition.getId())
                .ifPresent(latest -> {
                    if (definition.getVersion() <= latest.getVersion()) {
                        throw new IllegalArgumentException(
                                "Workflow version must be greater than latest version "
                                        + latest.getVersion()
                                        + " for workflow "
                                        + definition.getId()
                        );
                    }
                });
    }

    private void validateConnections(Set<String> nodeIds, NodeConnections connections) {
        if (connections == null || connections.getMain() == null) {
            return;
        }
        for (var output : connections.getMain()) {
            if (output == null) {
                continue;
            }
            for (NodeConnectionTarget target : output) {
                if (target == null) {
                    continue;
                }
                if (!nodeIds.contains(target.getNodeId())) {
                    throw new IllegalArgumentException("Connections reference unknown target node: " + target.getNodeId());
                }
            }
        }
    }
}
