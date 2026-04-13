package com.luziatcode.demoworkflowengine.service;

import com.luziatcode.demoworkflowengine.service.workflow.domain.ConnectionTarget;
import com.luziatcode.demoworkflowengine.service.workflow.domain.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.domain.NodeConnections;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.LoopNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.MergeNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.NoteNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.StartNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.SwitchNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.custom.GenericNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.custom.HttpNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.custom.TimerNodeExecutor;
import com.luziatcode.demoworkflowengine.repository.WorkflowDefinitionRepository;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutorRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDefinitionServiceTest {

    private final WorkflowDefinitionService service =
            new WorkflowDefinitionService(
                    new WorkflowDefinitionRepository(),
                    new NodeExecutorRegistry(List.of(
                            new StartNodeExecutor(),
                            new SwitchNodeExecutor(),
                            new MergeNodeExecutor(),
                            new LoopNodeExecutor(),
                            new NoteNodeExecutor(),
                            new TimerNodeExecutor(),
                            new GenericNodeExecutor(),
                            new HttpNodeExecutor()
                    ))
            );

    @Test
    void saveAcceptsValidDefinition() {
        WorkflowDefinition saved = service.save(definition("workflow", 1, Map.of("start", connections(target("task", 0)))));

        assertEquals("workflow", saved.getId());
        assertEquals(1, saved.getVersion());
    }

    @Test
    void saveRejectsDuplicateNodeIds() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node("start", "Start", NodeType.START), node("start", "Duplicate", NodeType.GENERIC)));

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
        definition.setNodes(List.of(node("task", "Task", NodeType.GENERIC)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Exactly one start node is required", exception.getMessage());
    }

    @Test
    void saveRejectsMissingActionType() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);

        Node start = new Node();
        start.setNodeId("start");
        start.setName("Start");
        definition.setNodes(List.of(start));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Node type is required: start", exception.getMessage());
    }

    @Test
    void saveRejectsEdgesThatReferenceUnknownNodes() {
        WorkflowDefinition definition = definition("workflow", 1, Map.of("start", connections(target("missing", 0))));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Connections reference unknown target node: missing", exception.getMessage());
    }

    @Test
    void getLatestReturnsHighestVersion() {
        service.save(definition("workflow", 1, Map.of("start", connections(target("task", 0)))));
        service.save(definition("workflow", 2, Map.of("start", connections(target("task", 0)))));

        WorkflowDefinition latest = service.getLatest("workflow");

        assertEquals(2, latest.getVersion());
    }

    @Test
    void saveRejectsBlankNodeId() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node(" ", "Start", NodeType.START)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Node id is required", exception.getMessage());
    }

    @Test
    void saveDefaultsVersionToOneForN8nDefinitions() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setNodes(List.of(node("start", "Start", NodeType.START)));

        WorkflowDefinition saved = service.save(definition);

        assertEquals(1, saved.getVersion());
    }

    @Test
    void saveRejectsConnectionsThatReferenceUnknownNodes() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node("start", "Start", NodeType.START), node("task", "Task", NodeType.GENERIC)));
        definition.setConnections(Map.of("start", connections(target("missing", 0))));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Connections reference unknown target node: missing", exception.getMessage());
    }

    @Test
    void saveRejectsConnectionsThatReferenceUnknownSourceNode() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node("start", "Start", NodeType.START), node("task", "Task", NodeType.GENERIC)));
        definition.setConnections(Map.of("missing", connections(target("task", 0))));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Connections reference unknown source node: missing", exception.getMessage());
    }

    private static WorkflowDefinition definition(String id, int version, Map<String, NodeConnections> connections) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(id);
        definition.setVersion(version);
        definition.setNodes(List.of(node("start", "Start", NodeType.START), node("task", "Task", NodeType.GENERIC)));
        definition.setConnections(connections);
        return definition;
    }

    private static Node node(String nodeId, String name, NodeType type) {
        Node node = new Node();
        node.setNodeId(nodeId);
        node.setName(name);
        node.setType(type);
        return node;
    }

    private static NodeConnections connections(ConnectionTarget... targets) {
        NodeConnections connections = new NodeConnections();
        connections.setMain(List.of(List.of(targets)));
        return connections;
    }

    private static ConnectionTarget target(String nodeName, int index) {
        ConnectionTarget target = new ConnectionTarget();
        target.setNode(nodeName);
        target.setType("main");
        target.setIndex(index);
        return target;
    }
}
