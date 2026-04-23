package com.luziatcode.demoworkflowengine.service.workflow;

import com.luziatcode.demoworkflowengine.controller.WorkflowTriggerController;
import com.luziatcode.demoworkflowengine.dto.WorkflowExecutionResponse;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.repository.WorkflowDefinitionRepository;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnectionTarget;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnections;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.engine.ContextTemplateResolver;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutorRegistry;
import com.luziatcode.demoworkflowengine.service.workflow.engine.WorkflowEngine;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.EndNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.StartNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.observation.ExecutionObservationDispatcher;
import com.luziatcode.demoworkflowengine.service.workflow.observation.ExecutionStateChangeSupport;
import com.luziatcode.demoworkflowengine.service.workflow.trigger.CronWorkflowTriggerScheduler;
import com.luziatcode.demoworkflowengine.service.workflow.trigger.StartTriggerResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowTriggerTest {

    @Test
    @DisplayName("webhook 트리거는 최신 workflow를 실행하고 요청 body를 context에 기록한다")
    void webhookTriggerStartsLatestWorkflow() {
        Fixture fixture = fixture();
        fixture.definitionService.save(webhookDefinition("webhook-flow", 1, "secret-1"));
        fixture.definitionService.save(webhookDefinition("webhook-flow", 2, "secret-2"));

        WorkflowExecutionResponse response = fixture.webhookController.triggerWebhook(
                "webhook-flow",
                "secret-2",
                Map.of("payload", "hello")
        );

        assertEquals(ExecutionStatus.SUCCESS, response.status());
        assertEquals("webhook-flow", response.workflowId());
        assertEquals(2, response.workflowVersion());
        assertEquals("hello", response.context().get("payload"));

        @SuppressWarnings("unchecked")
        Map<String, Object> trigger = (Map<String, Object>) response.context().get("trigger");
        assertEquals("webhook", trigger.get("type"));
        assertEquals("webhook", response.context().get("startTriggerType"));
    }

    @Test
    @DisplayName("webhook 토큰이 다르면 실행을 거부한다")
    void webhookTriggerRejectsInvalidToken() {
        Fixture fixture = fixture();
        fixture.definitionService.save(webhookDefinition("webhook-flow", 1, "secret"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.webhookController.triggerWebhook("webhook-flow", "wrong", Map.of())
        );

        assertEquals("Invalid webhook token for workflow: webhook-flow", exception.getMessage());
    }

    @Test
    @DisplayName("cron 트리거 poller는 due 된 workflow를 한 번만 실행한다")
    void cronTriggerPollerRunsDueWorkflowOnce() {
        Fixture fixture = fixture();
        fixture.definitionService.save(cronDefinition("cron-flow", 1, "* * * * * *"));

        fixture.cronScheduler.pollCronTriggers();
        fixture.cronScheduler.pollCronTriggers();

        List<com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution> executions =
                fixture.workflowExecutionRepository.findAll();
        assertEquals(1, executions.size());
        assertEquals(ExecutionStatus.SUCCESS, executions.getFirst().getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> trigger = (Map<String, Object>) executions.getFirst().getContext().get("trigger");
        assertEquals("cron", trigger.get("type"));
        assertEquals("cron", executions.getFirst().getContext().get("startTriggerType"));
        assertEquals("cron-default", executions.getFirst().getContext().get("seed"));
    }

    private Fixture fixture() {
        StartTriggerResolver startTriggerResolver = new StartTriggerResolver();
        WorkflowDefinitionRepository workflowDefinitionRepository = new WorkflowDefinitionRepository();
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(
                new StartNodeExecutor(),
                new EndNodeExecutor()
        ));
        WorkflowDefinitionService definitionService = new WorkflowDefinitionService(
                workflowDefinitionRepository,
                nodeExecutorRegistry,
                startTriggerResolver
        );
        ExecutionStateChangeSupport stateChangeSupport = new ExecutionStateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                new ExecutionObservationDispatcher(List.of(), List.of(), new SyncTaskExecutor())
        );
        WorkflowEngine workflowEngine = new WorkflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new ContextTemplateResolver(),
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = new WorkflowExecutionService(
                workflowExecutionRepository,
                definitionService,
                workflowEngine,
                stateChangeSupport,
                startTriggerResolver
        );

        return new Fixture(
                workflowExecutionRepository,
                definitionService,
                new WorkflowTriggerController(definitionService, executionService, startTriggerResolver),
                new CronWorkflowTriggerScheduler(definitionService, executionService, startTriggerResolver)
        );
    }

    private WorkflowDefinition webhookDefinition(String workflowId, int version, String token) {
        WorkflowDefinition definition = baseDefinition(workflowId, version);
        definition.getNodes().getFirst().getMetadata().put("triggerType", "WEBHOOK");
        definition.getNodes().getFirst().getMetadata().put("webhookToken", token);
        return definition;
    }

    private WorkflowDefinition cronDefinition(String workflowId, int version, String cronExpression) {
        WorkflowDefinition definition = baseDefinition(workflowId, version);
        definition.getNodes().getFirst().getMetadata().put("triggerType", "CRON");
        definition.getNodes().getFirst().getMetadata().put("cron", cronExpression);
        definition.getNodes().getFirst().getMetadata().put("input", Map.of("seed", "cron-default"));
        return definition;
    }

    private WorkflowDefinition baseDefinition(String workflowId, int version) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(workflowId);
        definition.setVersion(version);
        definition.setName(workflowId + "-v" + version);
        definition.setNodes(List.of(
                node("node_start", "Start", NodeType.START),
                node("node_end", "Done", NodeType.END)
        ));

        NodeConnections startConnections = new NodeConnections();
        startConnections.setMain(List.of(List.of(target("node_end", 0))));
        Map<String, NodeConnections> connections = new LinkedHashMap<>();
        connections.put("node_start", startConnections);
        definition.setConnections(connections);
        return definition;
    }

    private Node node(String id, String name, NodeType type) {
        Node node = new Node();
        node.setId(id);
        node.setName(name);
        node.setType(type);
        return node;
    }

    private NodeConnectionTarget target(String nodeId, int index) {
        NodeConnectionTarget target = new NodeConnectionTarget();
        target.setNodeId(nodeId);
        target.setType("main");
        target.setIndex(index);
        return target;
    }

    private record Fixture(
            WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowDefinitionService definitionService,
            WorkflowTriggerController webhookController,
            CronWorkflowTriggerScheduler cronScheduler
    ) {
    }
}
