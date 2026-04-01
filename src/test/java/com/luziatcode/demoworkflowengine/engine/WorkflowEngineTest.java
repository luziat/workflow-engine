package com.luziatcode.demoworkflowengine.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luziatcode.demoworkflowengine.service.workflow.action.Action;
import com.luziatcode.demoworkflowengine.service.workflow.domain.*;
import com.luziatcode.demoworkflowengine.service.workflow.action.base.StartAction;
import com.luziatcode.demoworkflowengine.service.workflow.action.base.SwitchAction;
import com.luziatcode.demoworkflowengine.service.workflow.action.custom.GenericAction;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import com.luziatcode.demoworkflowengine.service.WorkflowExecutionService;
import com.luziatcode.demoworkflowengine.service.workflow.engine.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkflowEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runCompletesLinearWorkflowAndPersistsNodeExecutions() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartAction(), new GenericAction())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "send-email",
                  "version": 2,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "actionType": "START" },
                    { "nodeId": "task", "name": "Send Email", "actionType": "GENERIC" }
                  ],
                  "edges": [
                    { "edgeId": "start-task", "from": "start", "to": "task" }
                  ]
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of("requestId", "req-1"));

        WorkflowExecution result = engine.run(definition, execution);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNull(result.getCurrentNodeId());
        assertEquals(true, result.getContext().get("started"));
        assertEquals("Send Email", result.getContext().get("lastTask"));
        assertEquals("task", result.getContext().get("handledBy"));

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(0).getStatus());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(1).getStatus());
        assertEquals("start", nodeExecutions.get(0).getNodeId());
        assertEquals("task", nodeExecutions.get(1).getNodeId());
        assertEquals("req-1", nodeExecutions.get(0).getInput().get("requestId"));
    }

    @Test
    void runMarksExecutionFailedWhenExecutorThrows() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        Action failingExecutor = new Action() {
            @Override
            public ActionType getType() {
                return ActionType.GENERIC;
            }

            @Override
            public void execute(NodeExecutionContext context) {
                throw new IllegalStateException("boom");
            }
        };
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartAction(), failingExecutor)),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "failure-workflow",
                  "version": 1,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "actionType": "START" },
                    { "nodeId": "fail-node", "name": "Failure", "actionType": "GENERIC" }
                  ],
                  "edges": [
                    { "edgeId": "start-fail-node", "from": "start", "to": "fail-node" }
                  ]
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of());

        WorkflowExecution result = engine.run(definition, execution);

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertEquals("boom", result.getFailureMessage());
        assertEquals("fail-node", result.getCurrentNodeId());

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        NodeExecution failedNode = nodeExecutions.get(1);
        assertEquals(ExecutionStatus.FAILED, failedNode.getStatus());
        assertEquals("boom", failedNode.getMessage());
        assertNotNull(failedNode.getEndedAt());
    }

    @Test
    void runMarksExecutionFailedWhenMultipleEdgesMatch() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartAction(), new SwitchAction(), new GenericAction())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "branching-workflow",
                  "version": 3,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "actionType": "START" },
                    { "nodeId": "decision", "name": "Decision", "actionType": "SWITCH" },
                    { "nodeId": "task-a", "name": "Branch A", "actionType": "GENERIC" },
                    { "nodeId": "task-b", "name": "Branch B", "actionType": "GENERIC" }
                  ],
                  "edges": [
                    { "edgeId": "start-decision", "from": "start", "to": "decision" },
                    { "edgeId": "decision-task-a", "from": "decision", "to": "task-a", "condition": "score >= 10" },
                    { "edgeId": "decision-task-b", "from": "decision", "to": "task-b", "condition": "score > 5" }
                  ]
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of("score", 10));

        WorkflowExecution result = engine.run(definition, execution);

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertEquals("Multiple edges matched from node: decision", result.getFailureMessage());
        assertEquals("decision", result.getCurrentNodeId());
        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(execution.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        assertEquals(ExecutionStatus.FAILED, nodeExecutions.get(1).getStatus());
        assertEquals("decision", nodeExecutions.get(1).getNodeId());
        assertEquals("Multiple edges matched from node: decision", nodeExecutions.get(1).getMessage());
    }

    @Test
    void runUsesNullConditionAsElseWhenNoOtherEdgesMatch() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartAction(), new SwitchAction(), new GenericAction())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "else-branch-workflow",
                  "version": 1,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "actionType": "START" },
                    { "nodeId": "decision", "name": "Decision", "actionType": "SWITCH" },
                    { "nodeId": "task-a", "name": "Branch A", "actionType": "GENERIC" },
                    { "nodeId": "task-b", "name": "Branch B", "actionType": "GENERIC" },
                    { "nodeId": "task-else", "name": "Else Branch", "actionType": "GENERIC" }
                  ],
                  "edges": [
                    { "edgeId": "start-decision", "from": "start", "to": "decision" },
                    { "edgeId": "decision-task-a", "from": "decision", "to": "task-a", "condition": "score >= 10" },
                    { "edgeId": "decision-task-b", "from": "decision", "to": "task-b", "condition": "score > 5" },
                    { "edgeId": "decision-task-else", "from": "decision", "to": "task-else", "condition": null }
                  ]
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of("score", 3));

        WorkflowExecution result = engine.run(definition, execution);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNull(result.getCurrentNodeId());
        assertEquals("Else Branch", result.getContext().get("lastTask"));
        assertEquals("task-else", result.getContext().get("handledBy"));

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(3, nodeExecutions.size());
        assertEquals("start", nodeExecutions.get(0).getNodeId());
        assertEquals("decision", nodeExecutions.get(1).getNodeId());
        assertEquals("task-else", nodeExecutions.get(2).getNodeId());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(2).getStatus());
    }

    @Test
    void runPrefersMatchedConditionOverElseEdge() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartAction(), new SwitchAction(), new GenericAction())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "else-if-workflow",
                  "version": 1,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "actionType": "START" },
                    { "nodeId": "decision", "name": "Decision", "actionType": "SWITCH" },
                    { "nodeId": "task-a", "name": "Branch A", "actionType": "GENERIC" },
                    { "nodeId": "task-b", "name": "Branch B", "actionType": "GENERIC" },
                    { "nodeId": "task-else", "name": "Else Branch", "actionType": "GENERIC" }
                  ],
                  "edges": [
                    { "edgeId": "start-decision", "from": "start", "to": "decision" },
                    { "edgeId": "decision-task-a", "from": "decision", "to": "task-a", "condition": "score >= 10" },
                    { "edgeId": "decision-task-b", "from": "decision", "to": "task-b", "condition": "score > 5" },
                    { "edgeId": "decision-task-else", "from": "decision", "to": "task-else", "condition": null }
                  ]
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of("score", 6));

        WorkflowExecution result = engine.run(definition, execution);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNull(result.getCurrentNodeId());
        assertEquals("Branch B", result.getContext().get("lastTask"));
        assertEquals("task-b", result.getContext().get("handledBy"));

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(3, nodeExecutions.size());
        assertEquals("task-b", nodeExecutions.get(2).getNodeId());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(2).getStatus());
    }

    private WorkflowDefinition definition(String json) {
        try {
            return objectMapper.readValue(json, WorkflowDefinition.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse workflow definition json", exception);
        }
    }
}
