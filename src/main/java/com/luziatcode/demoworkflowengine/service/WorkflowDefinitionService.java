package com.luziatcode.demoworkflowengine.service;

import com.luziatcode.demoworkflowengine.domain.Edge;
import com.luziatcode.demoworkflowengine.domain.Node;
import com.luziatcode.demoworkflowengine.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.repository.WorkflowDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowDefinitionService {
    private final WorkflowDefinitionRepository repository;

    public WorkflowDefinitionService(WorkflowDefinitionRepository repository) {
        this.repository = repository;
    }

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
            if (!nodeIds.add(node.getId())) {
                throw new IllegalArgumentException("Duplicate node id: " + node.getId());
            }
        }
        for (Edge edge : definition.getEdges()) {
            if (!nodeIds.contains(edge.getFrom()) || !nodeIds.contains(edge.getTo())) {
                throw new IllegalArgumentException("Edge references unknown node: " + edge.getFrom() + " -> " + edge.getTo());
            }
        }
        long startCount = definition.getNodes().stream().filter(node -> "start".equals(node.getType())).count();
        if (startCount != 1) {
            throw new IllegalArgumentException("Exactly one start node is required");
        }
    }

    public WorkflowDefinition createSample() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("sample-workflow");
        definition.setVersion(1);

        Node start = new Node();
        start.setId("start");
        start.setType("start");

        Node http = new Node();
        http.setId("a");
        http.setType("http");
        http.setParams(Map.of("url", "https://example.org/api"));

        Node branch = new Node();
        branch.setId("b");
        branch.setType("switch");

        Node c1 = new Node();
        c1.setId("c1");
        c1.setType("task");
        c1.setParams(Map.of("name", "positive-branch"));

        Node c2 = new Node();
        c2.setId("c2");
        c2.setType("task");
        c2.setParams(Map.of("name", "negative-branch"));

        definition.setNodes(java.util.List.of(start, http, branch, c1, c2));

        Edge e1 = new Edge();
        e1.setFrom("start");
        e1.setTo("a");

        Edge e2 = new Edge();
        e2.setFrom("a");
        e2.setTo("b");

        Edge e3 = new Edge();
        e3.setFrom("b");
        e3.setTo("c1");
        e3.setCondition("x > 0");

        Edge e4 = new Edge();
        e4.setFrom("b");
        e4.setTo("c2");
        e4.setCondition("x <= 0");

        definition.setEdges(java.util.List.of(e1, e2, e3, e4));
        return save(definition);
    }
}
