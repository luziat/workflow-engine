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

    public WorkflowDefinition createSample() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("sample-workflow");
        definition.setVersion(1);

        Node start = new Node();
        start.setNodeId("start");
        start.setName("Start");
        start.setActionType(ActionType.START);

        Node http = new Node();
        http.setNodeId("a");
        http.setName("Fetch API");
        http.setActionType(ActionType.HTTP);
        http.setParams(Map.of("url", "https://example.org/api"));

        Node branch = new Node();
        branch.setNodeId("b");
        branch.setName("Branch");
        branch.setActionType(ActionType.SWITCH);

        Node c1 = new Node();
        c1.setNodeId("c1");
        c1.setName("Positive Branch");
        c1.setActionType(ActionType.GENERIC);

        Node c2 = new Node();
        c2.setNodeId("c2");
        c2.setName("Negative Branch");
        c2.setActionType(ActionType.GENERIC);

        definition.setNodes(java.util.List.of(start, http, branch, c1, c2));

        Edge e1 = new Edge();
        e1.setEdgeId("e1");
        e1.setFrom("start");
        e1.setTo("a");

        Edge e2 = new Edge();
        e2.setEdgeId("e2");
        e2.setFrom("a");
        e2.setTo("b");

        Edge e3 = new Edge();
        e3.setEdgeId("e3");
        e3.setFrom("b");
        e3.setTo("c1");
        e3.setCondition("x > 0");

        Edge e4 = new Edge();
        e4.setEdgeId("e4");
        e4.setFrom("b");
        e4.setTo("c2");
        e4.setCondition("x <= 0");

        definition.setEdges(java.util.List.of(e1, e2, e3, e4));
        return save(definition);
    }
}
