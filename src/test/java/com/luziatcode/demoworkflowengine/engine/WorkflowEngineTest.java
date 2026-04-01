package com.luziatcode.demoworkflowengine.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luziatcode.demoworkflowengine.service.workflow.domain.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.NodeExecution;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowExecution;
import com.luziatcode.demoworkflowengine.service.workflow.task.NodeTask;
import com.luziatcode.demoworkflowengine.service.workflow.task.basic.StartNodeTask;
import com.luziatcode.demoworkflowengine.service.workflow.task.basic.SwitchNodeTask;
import com.luziatcode.demoworkflowengine.service.workflow.task.custom.TaskNodeTask;
import com.luziatcode.demoworkflowengine.service.workflow.task.basic.WaitNodeTask;
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
                new NodeExecutorRegistry(List.of(new StartNodeTask(), new TaskNodeTask())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "send-email",
                  "version": 2,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "type": "start" },
                    { "nodeId": "task", "name": "Send Email", "type": "task" }
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
        assertNull(result.getWaitingNodeId());
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
    void runStopsAtWaitNodeAndMarksExecutionWaiting() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeTask(), new WaitNodeTask())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "approval-workflow",
                  "version": 1,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "type": "start" },
                    { "nodeId": "approval", "name": "Approval Wait", "type": "wait" }
                  ],
                  "edges": [
                    { "edgeId": "start-approval", "from": "start", "to": "approval" }
                  ]
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of());

        WorkflowExecution result = engine.run(definition, execution);

        assertEquals(ExecutionStatus.WAITING, result.getStatus());
        assertEquals("approval", result.getCurrentNodeId());
        assertEquals("approval", result.getWaitingNodeId());

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        NodeExecution waitExecution = nodeExecutions.get(1);
        assertEquals(ExecutionStatus.WAITING, waitExecution.getStatus());
        assertEquals("Waiting for external resume input", waitExecution.getMessage());
        assertNotNull(waitExecution.getEndedAt());
    }

    @Test
    void runResumesFromWaitingExecution() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        NodeTask resumableWaitExecutor = new NodeTask() {
            @Override
            public String getType() {
                return "approval-wait";
            }

            @Override
            public NodeResult execute(NodeExecutionContext context) {
                if (Boolean.TRUE.equals(context.execution().getContext().get("approved"))) {
                    return NodeResult.next(Map.of("approvedBy", "reviewer"));
                }
                return NodeResult.waitForSignal("Waiting for approval");
            }
        };
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeTask(), resumableWaitExecutor, new TaskNodeTask())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "resume-workflow",
                  "version": 1,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "type": "start" },
                    { "nodeId": "approval", "name": "Approval", "type": "approval-wait" },
                    { "nodeId": "task", "name": "Send Email", "type": "task" }
                  ],
                  "edges": [
                    { "edgeId": "start-approval", "from": "start", "to": "approval" },
                    { "edgeId": "approval-task", "from": "approval", "to": "task" }
                  ]
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of());

        WorkflowExecution waiting = engine.run(definition, execution);

        assertEquals(ExecutionStatus.WAITING, waiting.getStatus());
        assertEquals("approval", waiting.getCurrentNodeId());
        assertEquals("approval", waiting.getWaitingNodeId());

        waiting.getContext().put("approved", true);
        waiting.setWaitingNodeId(null);
        WorkflowExecution resumed = engine.run(definition, executionService.update(waiting));

        assertEquals(ExecutionStatus.SUCCESS, resumed.getStatus());
        assertNull(resumed.getCurrentNodeId());
        assertNull(resumed.getWaitingNodeId());
        assertEquals(true, resumed.getContext().get("approved"));
        assertEquals("reviewer", resumed.getContext().get("approvedBy"));
        assertEquals("Send Email", resumed.getContext().get("lastTask"));
        assertEquals("task", resumed.getContext().get("handledBy"));

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(resumed.getExecutionId());
        assertEquals(4, nodeExecutions.size());
        assertEquals("start", nodeExecutions.get(0).getNodeId());
        assertEquals(ExecutionStatus.WAITING, nodeExecutions.get(1).getStatus());
        assertEquals("approval", nodeExecutions.get(1).getNodeId());
        assertEquals("approval", nodeExecutions.get(2).getNodeId());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(2).getStatus());
        assertEquals("task", nodeExecutions.get(3).getNodeId());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(3).getStatus());
    }

    @Test
    void runMarksExecutionFailedWhenExecutorThrows() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        NodeTask failingExecutor = new NodeTask() {
            @Override
            public String getType() {
                return "fail";
            }

            @Override
            public NodeResult execute(NodeExecutionContext context) {
                throw new IllegalStateException("boom");
            }
        };
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeTask(), failingExecutor)),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "failure-workflow",
                  "version": 1,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "type": "start" },
                    { "nodeId": "fail-node", "name": "Failure", "type": "fail" }
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
                new NodeExecutorRegistry(List.of(new StartNodeTask(), new SwitchNodeTask(), new TaskNodeTask())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "branching-workflow",
                  "version": 3,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "type": "start" },
                    { "nodeId": "decision", "name": "Decision", "type": "switch" },
                    { "nodeId": "task-a", "name": "Branch A", "type": "task" },
                    { "nodeId": "task-b", "name": "Branch B", "type": "task" }
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
                new NodeExecutorRegistry(List.of(new StartNodeTask(), new SwitchNodeTask(), new TaskNodeTask())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "else-branch-workflow",
                  "version": 1,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "type": "start" },
                    { "nodeId": "decision", "name": "Decision", "type": "switch" },
                    { "nodeId": "task-a", "name": "Branch A", "type": "task" },
                    { "nodeId": "task-b", "name": "Branch B", "type": "task" },
                    { "nodeId": "task-else", "name": "Else Branch", "type": "task" }
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
                new NodeExecutorRegistry(List.of(new StartNodeTask(), new SwitchNodeTask(), new TaskNodeTask())),
                executionService,
                nodeExecutionRepository,
                new SimpleConditionEvaluator()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "else-if-workflow",
                  "version": 1,
                  "nodes": [
                    { "nodeId": "start", "name": "Start", "type": "start" },
                    { "nodeId": "decision", "name": "Decision", "type": "switch" },
                    { "nodeId": "task-a", "name": "Branch A", "type": "task" },
                    { "nodeId": "task-b", "name": "Branch B", "type": "task" },
                    { "nodeId": "task-else", "name": "Else Branch", "type": "task" }
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
