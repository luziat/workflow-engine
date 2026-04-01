package com.luziatcode.demoworkflowengine.service;

import com.luziatcode.demoworkflowengine.service.workflow.domain.Edge;
import com.luziatcode.demoworkflowengine.service.workflow.domain.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDefinitionServiceTest {

    private final WorkflowDefinitionService service =
            new WorkflowDefinitionService(new WorkflowDefinitionRepository());

    @Test
    void saveAcceptsValidDefinition() {
        WorkflowDefinition saved = service.save(definition("workflow", 1, List.of(edge("start", "task"))));

        assertEquals("workflow", saved.getId());
        assertEquals(1, saved.getVersion());
    }

    @Test
    void saveRejectsDuplicateNodeIds() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node("start", "Start", "start"), node("start", "Duplicate", "task")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Duplicate node id: start", exception.getMessage());
    }

    @Test
    void saveRejectsMissingStartNode() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node("task", "Task", "task")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Exactly one start node is required", exception.getMessage());
    }

    @Test
    void saveRejectsEdgesThatReferenceUnknownNodes() {
        WorkflowDefinition definition = definition("workflow", 1, List.of(edge("start", "missing")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Edge references unknown node: start -> missing", exception.getMessage());
    }

    @Test
    void getLatestReturnsHighestVersion() {
        service.save(definition("workflow", 1, List.of(edge("start", "task"))));
        service.save(definition("workflow", 2, List.of(edge("start", "task"))));

        WorkflowDefinition latest = service.getLatest("workflow");

        assertEquals(2, latest.getVersion());
    }

    @Test
    void saveRejectsBlankNodeId() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node(" ", "Start", "start")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Node id is required", exception.getMessage());
    }

    private static WorkflowDefinition definition(String id, int version, List<Edge> edges) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(id);
        definition.setVersion(version);
        definition.setNodes(List.of(node("start", "Start", "start"), node("task", "Task", "task")));
        definition.setEdges(edges);
        return definition;
    }

    private static Node node(String nodeId, String name, String type) {
        Node node = new Node();
        node.setNodeId(nodeId);
        node.setName(name);
        node.setType(type);
        return node;
    }

    private static Edge edge(String from, String to) {
        Edge edge = new Edge();
        edge.setEdgeId(from + "-" + to);
        edge.setFrom(from);
        edge.setTo(to);
        return edge;
    }
}
