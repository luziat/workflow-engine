package com.luziatcode.demoworkflowengine.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.domain.*;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.LoopNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.MergeNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.NoteNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.StartNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.SwitchNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.custom.GenericNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.custom.HttpNodeExecutor;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import com.luziatcode.demoworkflowengine.service.WorkflowExecutionService;
import com.luziatcode.demoworkflowengine.service.workflow.engine.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkflowEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runCompletesLinearWorkflowAndPersistsNodeExecutions() {
        /* 저장소 */
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();

        /* 실행 서비스 (런타임) */
        WorkflowExecutionService executionService = new WorkflowExecutionService(workflowExecutionRepository);

        /* 엔진 */
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new GenericNodeExecutor()));
        WorkflowEngine engine = new WorkflowEngine(nodeExecutorRegistry, executionService, nodeExecutionRepository);

        WorkflowDefinition definition = definition("""
                {
                  "id": "send-email",
                  "version": 2,
                  "nodes": [
                    { "id": "start", "name": "시작", "type": "START", "params": {} },
                    { "id": "task", "name": "이메일 보내기", "type": "GENERIC", "params": {} }
                  ],
                  "connections": {
                    "start": {
                      "main": [
                        [
                          { "node": "task", "type": "main", "index": 0 }
                        ]
                      ]
                    }
                  }
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of("requestId", "req-1"));

        WorkflowExecution result = engine.run(execution);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNull(result.getCurrentNodeId());
        assertEquals(true, result.getContext().get("started"));
        assertEquals("이메일 보내기", result.getContext().get("lastTask"));
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
        NodeExecutor failingExecutor = new NodeExecutor() {
            @Override
            public NodeType getType() {
                return NodeType.GENERIC;
            }

            @Override
            public void execute(NodeExecutionContext context) {
                throw new IllegalStateException("boom");
            }
        };
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeExecutor(), failingExecutor)),
                executionService,
                nodeExecutionRepository
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "failure-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "start", "name": "Start", "type": "START", "params": {} },
                    { "id": "fail-node", "name": "Failure", "type": "GENERIC", "params": {} }
                  ],
                  "connections": {
                    "start": {
                      "main": [
                        [
                          { "node": "fail-node", "type": "main", "index": 0 }
                        ]
                      ]
                    }
                  }
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of());

        WorkflowExecution result = engine.run(execution);

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
    void runSupportsN8nConnectionsWithParallelBranchesAndMerge() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new HttpNodeExecutor(), new MergeNodeExecutor())),
                executionService,
                nodeExecutionRepository
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "branching-workflow",
                  "version": 3,
                  "nodes": [
                    { "id": "start", "name": "When clicking Execute workflow", "type": "START", "params": {} },
                    { "id": "http-1", "name": "HTTP Request", "type": "HTTP", "params": { "url": "http://a.example" } },
                    { "id": "http-2", "name": "HTTP Request 2", "type": "HTTP", "params": { "url": "http://b.example" } },
                    { "id": "http-3", "name": "HTTP Request 3", "type": "HTTP", "params": { "url": "http://c.example" } },
                    { "id": "merge", "name": "Merge", "type": "MERGE", "params": {} }
                  ],
                  "connections": {
                    "start": {
                      "main": [
                        [
                          { "node": "http-1", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "http-1": {
                      "main": [
                        [
                          { "node": "http-2", "type": "main", "index": 0 },
                          { "node": "http-3", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "http-2": {
                      "main": [
                        [
                          { "node": "merge", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "http-3": {
                      "main": [
                        [
                          { "node": "merge", "type": "main", "index": 1 }
                        ]
                      ]
                    }
                  }
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of());

        WorkflowExecution result = engine.run(execution);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("http://c.example", result.getContext().get("lastHttpUrl"));
        assertEquals(200, result.getContext().get("httpStatus"));
        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(execution.getExecutionId());
        assertEquals(5, nodeExecutions.size());
        assertEquals("merge", nodeExecutions.get(4).getNodeId());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(4).getStatus());
    }

    @Test
    void parsesActionTypeWorkflowJson() {
        WorkflowDefinition definition = definition("""
                {
                  "id": "8hKbtk3KW6TrsrH9",
                  "name": "My workflow",
                  "nodes": [
                    {
                      "params": {},
                      "type": "START",
                      "id": "acfabc3b-211e-40b7-a7c4-8f5c14b18338",
                      "name": "When clicking Execute workflow"
                    },
                    {
                      "params": {
                        "url": "http://www.naver.com",
                        "options": {}
                      },
                      "type": "HTTP",
                      "id": "07ff0d27-5843-4db9-bd0d-dbb88f5163a3",
                      "name": "HTTP Request"
                    }
                  ],
                  "connections": {
                    "acfabc3b-211e-40b7-a7c4-8f5c14b18338": {
                      "main": [
                        [
                          {
                            "node": "07ff0d27-5843-4db9-bd0d-dbb88f5163a3",
                            "type": "main",
                            "index": 0
                          }
                        ]
                      ]
                    }
                  }
                }
                """);

        assertEquals("8hKbtk3KW6TrsrH9", definition.getId());
        assertEquals("My workflow", definition.getName());
        assertEquals(NodeType.START, definition.getNodes().get(0).getType());
        assertEquals(NodeType.HTTP, definition.getNodes().get(1).getType());
        assertEquals("07ff0d27-5843-4db9-bd0d-dbb88f5163a3", definition.getConnections()
                .get("acfabc3b-211e-40b7-a7c4-8f5c14b18338")
                .getMain()
                .getFirst()
                .getFirst()
                .getNode());
    }

    @Test
    void stopMarksExecutionStoppedAndStopsCurrentAction() throws Exception {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(
                        new StartNodeExecutor(),
                        new com.luziatcode.demoworkflowengine.service.workflow.executor.custom.TimerNodeExecutor(),
                        new GenericNodeExecutor(),
                        new SwitchNodeExecutor(),
                        new HttpNodeExecutor(),
                        new MergeNodeExecutor(),
                        new LoopNodeExecutor(),
                        new NoteNodeExecutor()
                )),
                executionService,
                nodeExecutionRepository
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "stoppable-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "start", "name": "Start", "type": "START", "params": {} },
                    { "id": "wait", "name": "Wait", "type": "TIMER", "params": {} },
                    { "id": "task", "name": "Should Not Run", "type": "GENERIC", "params": {} }
                  ],
                  "connections": {
                    "start": {
                      "main": [
                        [
                          { "node": "wait", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "wait": {
                      "main": [
                        [
                          { "node": "task", "type": "main", "index": 0 }
                        ]
                      ]
                    }
                  }
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of());

        CompletableFuture<WorkflowExecution> future = CompletableFuture.supplyAsync(() -> engine.run(execution));

        awaitStatus(executionService, execution.getExecutionId(), ExecutionStatus.RUNNING);
        awaitCurrentNode(executionService, execution.getExecutionId(), "wait");

        WorkflowExecution stopping = engine.stop(execution.getExecutionId(), "manual stop");
        assertEquals(ExecutionStatus.STOPPING, stopping.getStatus());

        WorkflowExecution result = future.get(5, TimeUnit.SECONDS);

        assertEquals(ExecutionStatus.STOPPED, result.getStatus());
        assertNull(result.getCurrentNodeId());
        assertEquals("manual stop", result.getFailureMessage());
        assertNull(result.getContext().get("lastTask"));

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(0).getStatus());
        assertEquals(ExecutionStatus.STOPPED, nodeExecutions.get(1).getStatus());
        assertEquals("wait", nodeExecutions.get(1).getNodeId());
        assertEquals("manual stop", nodeExecutions.get(1).getMessage());
    }

    private WorkflowDefinition definition(String json) {
        try {
            return objectMapper.readValue(json, WorkflowDefinition.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse workflow definition json", exception);
        }
    }

    private void awaitStatus(WorkflowExecutionService executionService, String executionId, ExecutionStatus status) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (executionService.getRequired(executionId).getStatus() == status) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Timed out waiting for status " + status);
    }

    private void awaitCurrentNode(WorkflowExecutionService executionService, String executionId, String nodeId) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (nodeId.equals(executionService.getRequired(executionId).getCurrentNodeId())) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Timed out waiting for current node " + nodeId);
    }
}
