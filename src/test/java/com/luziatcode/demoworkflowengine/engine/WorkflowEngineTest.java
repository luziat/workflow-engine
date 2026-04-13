package com.luziatcode.demoworkflowengine.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.NodeExecution;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.LoopNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.MergeNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.NoteNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.StartNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.base.SwitchNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.custom.HttpNodeExecutor;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import com.luziatcode.demoworkflowengine.service.WorkflowExecutionService;
import com.luziatcode.demoworkflowengine.service.workflow.engine.*;
import com.luziatcode.demoworkflowengine.service.workflow.executor.custom.TestNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.custom.TimerNodeExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("01.기본 실행 - 선형 워크플로우를 실행하고 노드 실행 이력을 저장한다")
    void runCompletesLinearWorkflowAndPersistsNodeExecutions() {
        /* 저장소 */
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();

        /* 실행 서비스 (런타임) */
        WorkflowExecutionService executionService = new WorkflowExecutionService(workflowExecutionRepository);

        /* 엔진 */
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new TestNodeExecutor()));
        WorkflowEngine engine = new WorkflowEngine(nodeExecutorRegistry, executionService, nodeExecutionRepository, new SyncTaskExecutor());

        WorkflowDefinition definition = definition("""
                {
                  "id": "send-email",
                  "version": 2,
                  "nodes": [
                    { "id": "node_start", "name": "시작", "type": "START", "params": {} },
                    { "id": "node_send_email", "name": "이메일 보내기", "type": "TEST", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [
                        [
                          { "nodeId": "node_send_email", "type": "main", "index": 0 }
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
        assertEquals("node_send_email", result.getContext().get("handledBy"));

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(0).getStatus());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(1).getStatus());
        assertEquals("node_start", nodeExecutions.get(0).getNodeId());
        assertEquals("node_send_email", nodeExecutions.get(1).getNodeId());
        assertEquals("req-1", nodeExecutions.get(0).getInput().get("requestId"));
    }

    @Test
    @DisplayName("02.노드에서 예외발생 - 노드 실행기가 예외를 던지면 실행을 FAILED로 기록한다")
    void runMarksExecutionFailedWhenExecutorThrows() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        NodeExecutor failingExecutor = new NodeExecutor() {
            @Override
            public NodeType getType() {
                return NodeType.TEST;
            }

            @Override
            public void execute(NodeExecutionContext context) {
                throw new IllegalStateException("boom");
            }
        };
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeExecutor(), failingExecutor)),
                executionService,
                nodeExecutionRepository,
                new SyncTaskExecutor()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "failure-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_failure", "name": "Failure", "type": "TEST", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [
                        [
                          { "nodeId": "node_failure", "type": "main", "index": 0 }
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
        assertEquals("node_failure", result.getCurrentNodeId());

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        NodeExecution failedNode = nodeExecutions.get(1);
        assertEquals(ExecutionStatus.FAILED, failedNode.getStatus());
        assertEquals("boom", failedNode.getMessage());
        assertNotNull(failedNode.getEndedAt());
    }

    @Test
    @DisplayName("03.병렬과 머지 - 병렬 분기와 머지 연결을 포함한 connections를 처리한다")
    void runSupportsN8nConnectionsWithParallelBranchesAndMerge() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new HttpNodeExecutor(), new MergeNodeExecutor())),
                executionService,
                nodeExecutionRepository,
                new SyncTaskExecutor()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "branching-workflow",
                  "version": 3,
                  "nodes": [
                    { "id": "node_start", "name": "When clicking Execute workflow", "type": "START", "params": {} },
                    { "id": "node_http_request_a", "name": "HTTP Request", "type": "HTTP", "params": { "url": "http://a.example" } },
                    { "id": "node_http_request_b", "name": "HTTP Request 2", "type": "HTTP", "params": { "url": "http://b.example" } },
                    { "id": "node_http_request_c", "name": "HTTP Request 3", "type": "HTTP", "params": { "url": "http://c.example" } },
                    { "id": "node_merge", "name": "Merge", "type": "MERGE", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [
                        [
                          { "nodeId": "node_http_request_a", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "node_http_request_a": {
                      "main": [
                        [
                          { "nodeId": "node_http_request_b", "type": "main", "index": 0 },
                          { "nodeId": "node_http_request_c", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "node_http_request_b": {
                      "main": [
                        [
                          { "nodeId": "node_merge", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "node_http_request_c": {
                      "main": [
                        [
                          { "nodeId": "node_merge", "type": "main", "index": 1 }
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
        assertEquals("node_merge", nodeExecutions.get(4).getNodeId());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(4).getStatus());
    }

    @Test
    @DisplayName("04.비동기 실행 - 비동기 실행 시작은 execution을 즉시 반환하고 백그라운드에서 상태를 진행시킨다")
    void runAsyncStartsExecutionInBackground() throws Exception {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new TimerNodeExecutor(), new TestNodeExecutor())),
                executionService,
                nodeExecutionRepository,
                new SimpleAsyncTaskExecutor("workflow-test-")
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "async-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_wait", "name": "Wait", "type": "TIMER", "params": {} },
                    { "id": "node_finish", "name": "Finish", "type": "TEST", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [
                        [
                          { "nodeId": "node_wait", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "node_wait": {
                      "main": [
                        [
                          { "nodeId": "node_finish", "type": "main", "index": 0 }
                        ]
                      ]
                    }
                  }
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of());

        WorkflowExecution started = engine.runAsync(execution);

        assertEquals(execution.getExecutionId(), started.getExecutionId());
        assertTrue(
                List.of(ExecutionStatus.READY, ExecutionStatus.RUNNING).contains(
                        executionService.getRequired(execution.getExecutionId()).getStatus()
                )
        );

        awaitStatus(executionService, execution.getExecutionId(), ExecutionStatus.RUNNING);
        awaitCurrentNode(executionService, execution.getExecutionId(), "node_wait");
        awaitStatus(executionService, execution.getExecutionId(), ExecutionStatus.SUCCESS);

        WorkflowExecution result = executionService.getRequired(execution.getExecutionId());
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNull(result.getCurrentNodeId());
        assertEquals("node_finish", result.getContext().get("handledBy"));
    }

    @Test
    @DisplayName("05.루프 분기 - items가 Iterable이면 output 0, 아니면 output 1로 진행한다")
    void loopExecutorSelectsOutputByItemsType() {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new LoopNodeExecutor(), new TestNodeExecutor())),
                executionService,
                nodeExecutionRepository,
                new SyncTaskExecutor()
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "loop-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_loop", "name": "Loop", "type": "LOOP", "params": {} },
                    { "id": "node_loop_body", "name": "Loop Body", "type": "TEST", "params": {} },
                    { "id": "node_loop_done", "name": "Loop Done", "type": "TEST", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [
                        [
                          { "nodeId": "node_loop", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "node_loop": {
                      "main": [
                        [
                          { "nodeId": "node_loop_body", "type": "main", "index": 0 }
                        ],
                        [
                          { "nodeId": "node_loop_done", "type": "main", "index": 0 }
                        ]
                      ]
                    }
                  }
                }
                """);

        WorkflowExecution iterableExecution = executionService.create(definition, Map.of("items", List.of("a", "b")));
        WorkflowExecution iterableResult = engine.run(iterableExecution);
        assertEquals(ExecutionStatus.SUCCESS, iterableResult.getStatus());
        assertEquals("node_loop_body", iterableResult.getContext().get("handledBy"));

        WorkflowExecution scalarExecution = executionService.create(definition, Map.of("items", "not-iterable"));
        WorkflowExecution scalarResult = engine.run(scalarExecution);
        assertEquals(ExecutionStatus.SUCCESS, scalarResult.getStatus());
        assertEquals("node_loop_done", scalarResult.getContext().get("handledBy"));
    }

    @Test
    @DisplayName("06.실행 중 중지 명령 - 중지 요청 시 현재 실행 중인 노드를 멈추고 실행 상태를 STOPPED로 전환한다")
    void stopMarksExecutionStoppedAndStopsCurrentAction() throws Exception {
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        WorkflowExecutionService executionService = new WorkflowExecutionService(new WorkflowExecutionRepository());
        WorkflowEngine engine = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(
                        new StartNodeExecutor(),
                        new TimerNodeExecutor(),
                        new TestNodeExecutor(),
                        new SwitchNodeExecutor(),
                        new HttpNodeExecutor(),
                        new MergeNodeExecutor(),
                        new LoopNodeExecutor(),
                        new NoteNodeExecutor()
                )),
                executionService,
                nodeExecutionRepository,
                new SimpleAsyncTaskExecutor("workflow-stop-test-")
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "stoppable-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_wait", "name": "Wait", "type": "TIMER", "params": {} },
                    { "id": "node_should_not_run", "name": "Should Not Run", "type": "TEST", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [
                        [
                          { "nodeId": "node_wait", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "node_wait": {
                      "main": [
                        [
                          { "nodeId": "node_should_not_run", "type": "main", "index": 0 }
                        ]
                      ]
                    }
                  }
                }
                """);
        WorkflowExecution execution = executionService.create(definition, Map.of());

        WorkflowExecution started = engine.runAsync(execution);
        assertEquals(execution.getExecutionId(), started.getExecutionId());

        awaitStatus(executionService, execution.getExecutionId(), ExecutionStatus.RUNNING);
        awaitCurrentNode(executionService, execution.getExecutionId(), "node_wait");

        WorkflowExecution stopping = engine.stop(execution.getExecutionId(), "manual stop");
        assertEquals(ExecutionStatus.STOPPING, stopping.getStatus());

        awaitStatus(executionService, execution.getExecutionId(), ExecutionStatus.STOPPED);
        WorkflowExecution result = executionService.getRequired(execution.getExecutionId());

        assertEquals(ExecutionStatus.STOPPED, result.getStatus());
        assertNull(result.getCurrentNodeId());
        assertEquals("manual stop", result.getFailureMessage());
        assertNull(result.getContext().get("lastTask"));

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(0).getStatus());
        assertEquals(ExecutionStatus.STOPPED, nodeExecutions.get(1).getStatus());
        assertEquals("node_wait", nodeExecutions.get(1).getNodeId());
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
        for (int attempt = 0; attempt < 500; attempt++) {
            if (executionService.getRequired(executionId).getStatus() == status) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Timed out waiting for status " + status);
    }

    private void awaitCurrentNode(WorkflowExecutionService executionService, String executionId, String nodeId) throws InterruptedException {
        for (int attempt = 0; attempt < 500; attempt++) {
            if (nodeId.equals(executionService.getRequired(executionId).getCurrentNodeId())) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Timed out waiting for current node " + nodeId);
    }
}
