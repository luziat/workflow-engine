package com.luziatcode.demoworkflowengine.service;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnectionTarget;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeOutputs;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
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
        WorkflowDefinition saved = service.save(definition("workflow", 1, Map.of("node_start", connections(target("node_task", 0)))));

        assertEquals("workflow", saved.getId());
        assertEquals(1, saved.getVersion());
    }

    @Test
    void saveRejectsDuplicateNodeIds() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node("node_start", "Start", NodeType.START), node("node_start", "Duplicate", NodeType.GENERIC)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Duplicate node id: node_start", exception.getMessage());
    }

    @Test
    void saveRejectsMissingStartNode() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node("node_task", "Task", NodeType.GENERIC)));

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
        start.setId("node_start");
        start.setName("Start");
        definition.setNodes(List.of(start));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Node type is required: node_start", exception.getMessage());
    }

    @Test
    void saveRejectsEdgesThatReferenceUnknownNodes() {
        WorkflowDefinition definition = definition("workflow", 1, Map.of("node_start", connections(target("node_missing", 0))));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Connections reference unknown target node: node_missing", exception.getMessage());
    }

    @Test
    void getLatestReturnsHighestVersion() {
        service.save(definition("workflow", 1, Map.of("node_start", connections(target("node_task", 0)))));
        service.save(definition("workflow", 2, Map.of("node_start", connections(target("node_task", 0)))));

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
        definition.setNodes(List.of(node("node_start", "Start", NodeType.START)));

        WorkflowDefinition saved = service.save(definition);

        assertEquals(1, saved.getVersion());
    }

    @Test
    void saveRejectsConnectionsThatReferenceUnknownNodes() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node("node_start", "Start", NodeType.START), node("node_task", "Task", NodeType.GENERIC)));
        definition.setConnections(Map.of("node_start", connections(target("node_missing", 0))));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Connections reference unknown target node: node_missing", exception.getMessage());
    }

    @Test
    void saveRejectsConnectionsThatReferenceUnknownSourceNode() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("workflow");
        definition.setVersion(1);
        definition.setNodes(List.of(node("node_start", "Start", NodeType.START), node("node_task", "Task", NodeType.GENERIC)));
        definition.setConnections(Map.of("node_missing", connections(target("node_task", 0))));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("Connections reference unknown source node: node_missing", exception.getMessage());
    }

    private static WorkflowDefinition definition(String id, int version, Map<String, NodeOutputs> connections) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(id);
        definition.setVersion(version);
        definition.setNodes(List.of(node("node_start", "Start", NodeType.START), node("node_task", "Task", NodeType.GENERIC)));
        definition.setConnections(connections);
        return definition;
    }

    private static Node node(String nodeId, String name, NodeType type) {
        Node node = new Node();
        node.setId(nodeId);
        node.setName(name);
        node.setType(type);
        return node;
    }

    private static NodeOutputs connections(NodeConnectionTarget... targets) {
        NodeOutputs connections = new NodeOutputs();
        connections.setMain(List.of(List.of(targets)));
        return connections;
    }

    private static NodeConnectionTarget target(String nodeName, int index) {
        NodeConnectionTarget target = new NodeConnectionTarget();
        target.setNode(nodeName);
        target.setType("main");
        target.setIndex(index);
        return target;
    }
}
