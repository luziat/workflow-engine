package com.luziatcode.demoworkflowengine.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luziatcode.demoworkflowengine.repository.WorkflowDefinitionRepository;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.NodeExecution;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.LoopNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.MergeNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.StartNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.SwitchNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.IfNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.task.HttpNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.task.NoteNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.task.AINodeExecutor;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import com.luziatcode.demoworkflowengine.service.workflow.WorkflowDefinitionService;
import com.luziatcode.demoworkflowengine.service.workflow.WorkflowExecutionService;
import com.luziatcode.demoworkflowengine.service.workflow.engine.*;
import com.luziatcode.demoworkflowengine.service.workflow.observation.ExecutionObservationDispatcher;
import com.luziatcode.demoworkflowengine.service.workflow.observation.ExecutionStateChangeSupport;
import com.luziatcode.demoworkflowengine.service.workflow.observation.NodeExecutionListener;
import com.luziatcode.demoworkflowengine.service.workflow.observation.WorkflowExecutionListener;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.EndNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.task.TimerNodeExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        /* 엔진 */
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new EndNodeExecutor()));
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );

        WorkflowDefinition definition = definition("""
                {
                  "id": "send-email",
                  "version": 2,
                  "nodes": [
                    { "id": "node_start", "name": "시작", "type": "START", "params": {} },
                    { "id": "node_send_email", "name": "이메일 보내기", "type": "END", "params": {} }
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
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutor failingExecutor = new NodeExecutor() {
            @Override
            public NodeType getType() {
                return NodeType.END;
            }

            @Override
            public void execute(NodeExecutionContext context) {
                throw new IllegalStateException("boom");
            }
        };
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), failingExecutor));
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "failure-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_failure", "name": "Failure", "type": "END", "params": {} }
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
    void runSupportsN8nConnectionsWithParallelBranchesAndMerge() throws Exception {
        try (TestHttpServer server = startHttpServer(exchange ->
                writeJson(exchange, 200, Map.of("path", exchange.getRequestURI().getPath())))) {
            WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
            NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
            NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new HttpNodeExecutor(), new MergeNodeExecutor()));
            ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                    workflowExecutionRepository,
                    nodeExecutionRepository,
                    List.of(),
                    List.of(),
                    new SyncTaskExecutor()
            );
            WorkflowEngine engine = workflowEngine(
                    nodeExecutorRegistry,
                    workflowExecutionRepository,
                    stateChangeSupport,
                    new SyncTaskExecutor()
            );
            WorkflowExecutionService executionService = workflowExecutionService(
                    workflowExecutionRepository,
                    new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                    engine,
                    stateChangeSupport
            );
            WorkflowDefinition definition = definition("""
                    {
                      "id": "branching-workflow",
                      "version": 3,
                      "nodes": [
                        { "id": "node_start", "name": "When clicking Execute workflow", "type": "START", "params": {} },
                        { "id": "node_http_request_a", "name": "HTTP Request", "type": "HTTP", "params": { "url": "<<baseUrl>>/a" } },
                        { "id": "node_http_request_b", "name": "HTTP Request 2", "type": "HTTP", "params": { "url": "<<baseUrl>>/b" } },
                        { "id": "node_http_request_c", "name": "HTTP Request 3", "type": "HTTP", "params": { "url": "<<baseUrl>>/c" } },
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
            WorkflowExecution execution = executionService.create(definition, Map.of("baseUrl", server.baseUrl()));

            WorkflowExecution result = engine.run(execution);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertEquals(server.url("/c"), result.getContext().get("lastHttpUrl"));
            assertEquals(200, result.getContext().get("httpStatus"));
            List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(execution.getExecutionId());
            assertEquals(5, nodeExecutions.size());
            assertEquals("node_merge", nodeExecutions.get(4).getNodeId());
            assertEquals(ExecutionStatus.SUCCESS, nodeExecutions.get(4).getStatus());
        }
    }

    @Test
    @DisplayName("04.비동기 실행 - 비동기 실행 시작은 execution을 즉시 반환하고 백그라운드에서 상태를 진행시킨다")
    void runAsyncStartsExecutionInBackground() throws Exception {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new TimerNodeExecutor(), new EndNodeExecutor()));
        WorkflowDefinitionService definitionService =
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry);
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SimpleAsyncTaskExecutor("workflow-test-")
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                definitionService,
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "async-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_wait", "name": "Wait", "type": "TIMER", "params": {} },
                    { "id": "node_finish", "name": "Finish", "type": "END", "params": {} }
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
        definitionService.save(definition);

        WorkflowExecution started = executionService.start(definition.getId(), Map.of());

        assertNotNull(started.getExecutionId());
        assertTrue(
                List.of(ExecutionStatus.READY, ExecutionStatus.RUNNING).contains(
                        executionService.getRequired(started.getExecutionId()).getStatus()
                )
        );

        awaitStatus(executionService, started.getExecutionId(), ExecutionStatus.RUNNING);
        awaitCurrentNode(executionService, started.getExecutionId(), "node_wait");
        awaitStatus(executionService, started.getExecutionId(), ExecutionStatus.SUCCESS);

        WorkflowExecution result = executionService.getRequired(started.getExecutionId());
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNull(result.getCurrentNodeId());
        assertEquals("node_finish", result.getContext().get("handledBy"));
    }

    @Test
    @DisplayName("05.루프 분기 - items가 Iterable이면 output 0, 아니면 output 1로 진행한다")
    void loopExecutorSelectsOutputByItemsType() {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new LoopNodeExecutor(), new EndNodeExecutor()));
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "loop-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_loop", "name": "Loop", "type": "LOOP", "params": {} },
                    { "id": "node_loop_body", "name": "Loop Body", "type": "END", "params": {} },
                    { "id": "node_loop_done", "name": "Loop Done", "type": "END", "params": {} }
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
    @DisplayName("06.스위치 숫자비교 - 숫자 조건으로 true/false branch를 선택한다")
    void switchExecutorSupportsNumericConditions() {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new SwitchNodeExecutor(), new EndNodeExecutor()));
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "switch-number-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_switch", "name": "Switch", "type": "SWITCH", "params": {
                      "conditions": {
                        "conditions": [
                          {
                            "leftValue": "10",
                            "rightValue": 5,
                            "operator": { "operation": "greaterThan" }
                          }
                        ]
                      }
                    } },
                    { "id": "node_true", "name": "True", "type": "END", "params": {} },
                    { "id": "node_false", "name": "False", "type": "END", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [
                        [
                          { "nodeId": "node_switch", "type": "main", "index": 0 }
                        ]
                      ]
                    },
                    "node_switch": {
                      "main": [
                        [
                          { "nodeId": "node_true", "type": "main", "index": 0 }
                        ],
                        [
                          { "nodeId": "node_false", "type": "main", "index": 0 }
                        ]
                      ]
                    }
                  }
                }
                """);

        WorkflowExecution trueResult = engine.run(executionService.create(definition, Map.of()));
        assertEquals(ExecutionStatus.SUCCESS, trueResult.getStatus());
        assertEquals("node_true", trueResult.getContext().get("handledBy"));

        definition.getNodes().stream()
                .filter(node -> "node_switch".equals(node.getId()))
                .findFirst()
                .orElseThrow()
                .setParams(Map.of(
                        "conditions", Map.of(
                                "conditions", List.of(
                                        Map.of(
                                                "leftValue", 3,
                                                "rightValue", "5",
                                                "operator", Map.of("operation", "greaterThanOrEqual")
                                        )
                                )
                        )
                ));

        WorkflowExecution falseResult = engine.run(executionService.create(definition, Map.of()));
        assertEquals(ExecutionStatus.SUCCESS, falseResult.getStatus());
        assertEquals("node_false", falseResult.getContext().get("handledBy"));
    }

    @Test
    @DisplayName("07.IF 조건분기 - IF 노드는 별도 타입으로 true/false branch를 선택한다")
    void ifExecutorSelectsBranchWithDedicatedNodeType() {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new IfNodeExecutor(), new EndNodeExecutor()));
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "if-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_if", "name": "If", "type": "IF", "params": {
                      "conditions": {
                        "conditions": [
                          {
                            "leftValue": "<<order.totalAmount>>",
                            "rightValue": 10000,
                            "operator": { "operation": "greaterThanOrEqual" }
                          }
                        ]
                      }
                    } },
                    { "id": "node_true", "name": "Approved", "type": "END", "params": {} },
                    { "id": "node_false", "name": "Rejected", "type": "END", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [[{ "nodeId": "node_if", "type": "main", "index": 0 }]]
                    },
                    "node_if": {
                      "main": [
                        [{ "nodeId": "node_true", "type": "main", "index": 0 }],
                        [{ "nodeId": "node_false", "type": "main", "index": 0 }]
                      ]
                    }
                  }
                }
                """);

        WorkflowExecution trueResult = engine.run(executionService.create(definition, Map.of("order", Map.of("totalAmount", 10000))));
        assertEquals(ExecutionStatus.SUCCESS, trueResult.getStatus());
        assertEquals("node_true", trueResult.getContext().get("handledBy"));

        WorkflowExecution falseResult = engine.run(executionService.create(definition, Map.of("order", Map.of("totalAmount", 9999))));
        assertEquals(ExecutionStatus.SUCCESS, falseResult.getStatus());
        assertEquals("node_false", falseResult.getContext().get("handledBy"));
    }

    @Test
    @DisplayName("08.변수 치환 - params 전체에서 context 값을 찾아 치환한다")
    void resolvesTemplateVariablesAcrossNodeParams() throws Exception {
        try (TestHttpServer server = startHttpServer(exchange ->
                writeJson(exchange, 200, Map.of("path", exchange.getRequestURI().getPath())))) {
            WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
            NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
            NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(
                    new StartNodeExecutor(),
                    new HttpNodeExecutor(),
                    new TimerNodeExecutor(),
                    new SwitchNodeExecutor(),
                    new EndNodeExecutor()
            ));
            ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                    workflowExecutionRepository,
                    nodeExecutionRepository,
                    List.of(),
                    List.of(),
                    new SyncTaskExecutor()
            );
            WorkflowEngine engine = workflowEngine(
                    nodeExecutorRegistry,
                    workflowExecutionRepository,
                    stateChangeSupport,
                    new SyncTaskExecutor()
            );
            WorkflowExecutionService executionService = workflowExecutionService(
                    workflowExecutionRepository,
                    new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                    engine,
                    stateChangeSupport
            );
            WorkflowDefinition definition = definition("""
                    {
                      "id": "template-workflow",
                      "version": 1,
                      "nodes": [
                        { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                        { "id": "node_wait", "name": "Wait", "type": "TIMER", "params": { "waitMillis": "<<delayMillis>>" } },
                        { "id": "node_http", "name": "HTTP", "type": "HTTP", "params": { "url": "<<baseUrl>>/<<request.id>>/tenants/<<tenantId>>" } },
                        { "id": "node_switch", "name": "Switch", "type": "SWITCH", "params": {
                          "conditions": {
                            "conditions": [
                              {
                                "leftValue": "<<threshold.current>>",
                                "rightValue": "<<threshold.minimum>>",
                                "operator": { "operation": "greaterThan" }
                              }
                            ]
                          }
                        } },
                        { "id": "node_done", "name": "", "type": "END", "params": { "name": "Task <<request.id>>" } },
                        { "id": "node_fallback", "name": "Fallback", "type": "END", "params": {} }
                      ],
                      "connections": {
                        "node_start": {
                          "main": [[{ "nodeId": "node_wait", "type": "main", "index": 0 }]]
                        },
                        "node_wait": {
                          "main": [[{ "nodeId": "node_http", "type": "main", "index": 0 }]]
                        },
                        "node_http": {
                          "main": [[{ "nodeId": "node_switch", "type": "main", "index": 0 }]]
                        },
                        "node_switch": {
                          "main": [
                            [{ "nodeId": "node_done", "type": "main", "index": 0 }],
                            [{ "nodeId": "node_fallback", "type": "main", "index": 0 }]
                          ]
                        }
                      }
                    }
                    """);

            WorkflowExecution result = engine.run(executionService.create(definition, Map.of(
                    "baseUrl", server.baseUrl(),
                    "delayMillis", "0",
                    "tenantId", "tenant-1",
                    "request", Map.of("id", "req-42"),
                    "threshold", Map.of("current", "10", "minimum", 5)
            )));

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertEquals(server.url("/req-42/tenants/tenant-1"), result.getContext().get("lastHttpUrl"));
            assertEquals("Task req-42", result.getContext().get("lastTask"));
            assertEquals("node_done", result.getContext().get("handledBy"));
        }
    }

    @Test
    @DisplayName("09.변수 치환 실패 - context에 없는 변수는 노드와 워크플로우를 FAILED로 만든다")
    void failsExecutionWhenTemplateVariableIsMissing() {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new HttpNodeExecutor()));
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "missing-template-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_http", "name": "HTTP", "type": "HTTP", "params": { "url": "https://api.example.com/<<request.id>>" } }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [[{ "nodeId": "node_http", "type": "main", "index": 0 }]]
                    }
                  }
                }
                """);

        WorkflowExecution result = engine.run(executionService.create(definition, Map.of()));

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertEquals("Template variable not found: request.id", result.getFailureMessage());

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        assertEquals(ExecutionStatus.FAILED, nodeExecutions.get(1).getStatus());
        assertEquals("Template variable not found: request.id", nodeExecutions.get(1).getMessage());
    }

    @Test
    @DisplayName("10.HTTP 노드 실행 - method, queryParams, headers, body를 전송하고 구조화된 응답을 context에 기록한다")
    void httpExecutorSendsRequestAndStoresStructuredResponse() throws Exception {
        try (TestHttpServer server = startHttpServer(exchange -> {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("method", exchange.getRequestMethod());
            response.put("path", exchange.getRequestURI().getPath());
            response.put("query", exchange.getRequestURI().getQuery());
            response.put("tenantHeader", exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
            response.put("contentType", exchange.getRequestHeaders().getFirst("Content-Type"));
            response.put("body", requestBody(exchange));
            writeJson(exchange, 200, response);
        })) {
            WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
            NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
            NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(
                    new StartNodeExecutor(),
                    new HttpNodeExecutor(),
                    new EndNodeExecutor()
            ));
            ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                    workflowExecutionRepository,
                    nodeExecutionRepository,
                    List.of(),
                    List.of(),
                    new SyncTaskExecutor()
            );
            WorkflowEngine engine = workflowEngine(
                    nodeExecutorRegistry,
                    workflowExecutionRepository,
                    stateChangeSupport,
                    new SyncTaskExecutor()
            );
            WorkflowExecutionService executionService = workflowExecutionService(
                    workflowExecutionRepository,
                    new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                    engine,
                    stateChangeSupport
            );
            WorkflowDefinition definition = definition("""
                    {
                      "id": "http-request-workflow",
                      "version": 1,
                      "nodes": [
                        { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                        { "id": "node_http", "name": "HTTP", "type": "HTTP", "params": {
                          "method": "POST",
                          "url": "<<baseUrl>>/orders/<<order.id>>",
                          "headers": {
                            "X-Tenant-Id": "<<tenantId>>"
                          },
                          "queryParams": {
                            "include": "details",
                            "requestId": "<<requestId>>"
                          },
                          "body": {
                            "status": "<<order.status>>"
                          },
                          "timeoutMillis": 3000
                        } },
                        { "id": "node_end", "name": "Done", "type": "END", "params": {} }
                      ],
                      "connections": {
                        "node_start": {
                          "main": [[{ "nodeId": "node_http", "type": "main", "index": 0 }]]
                        },
                        "node_http": {
                          "main": [[{ "nodeId": "node_end", "type": "main", "index": 0 }]]
                        }
                      }
                    }
                    """);

            WorkflowExecution result = engine.run(executionService.create(definition, Map.of(
                    "baseUrl", server.baseUrl(),
                    "order", Map.of("id", "ord-1", "status", "PAID"),
                    "tenantId", "tenant-1",
                    "requestId", "req-9"
            )));

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertEquals(server.url("/orders/ord-1?include=details&requestId=req-9"), result.getContext().get("lastHttpUrl"));
            assertEquals(200, result.getContext().get("httpStatus"));

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getContext().get("httpBody");
            assertEquals("POST", body.get("method"));
            assertEquals("/orders/ord-1", body.get("path"));
            assertEquals("include=details&requestId=req-9", body.get("query"));
            assertEquals("tenant-1", body.get("tenantHeader"));
            assertTrue(String.valueOf(body.get("contentType")).contains("application/json"));
            assertEquals("{\"status\":\"PAID\"}", body.get("body"));

            @SuppressWarnings("unchecked")
            Map<String, Object> httpOutputs = (Map<String, Object>) result.getContext().get("httpOutputs");
            @SuppressWarnings("unchecked")
            Map<String, Object> nodeOutput = (Map<String, Object>) httpOutputs.get("node_http");
            assertEquals("POST", nodeOutput.get("method"));
            assertEquals(server.url("/orders/ord-1?include=details&requestId=req-9"), nodeOutput.get("url"));
            assertEquals(200, nodeOutput.get("status"));
        }
    }

    @Test
    @DisplayName("11.HTTP 실패 전파 - non-2xx 응답이면 노드와 워크플로우를 FAILED로 만든다")
    void httpExecutorFailsWorkflowOnNon2xxResponse() throws Exception {
        try (TestHttpServer server = startHttpServer(exchange ->
                writeJson(exchange, 503, Map.of("error", "downstream unavailable")))) {
            WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
            NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
            NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(
                    new StartNodeExecutor(),
                    new HttpNodeExecutor()
            ));
            ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                    workflowExecutionRepository,
                    nodeExecutionRepository,
                    List.of(),
                    List.of(),
                    new SyncTaskExecutor()
            );
            WorkflowEngine engine = workflowEngine(
                    nodeExecutorRegistry,
                    workflowExecutionRepository,
                    stateChangeSupport,
                    new SyncTaskExecutor()
            );
            WorkflowExecutionService executionService = workflowExecutionService(
                    workflowExecutionRepository,
                    new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                    engine,
                    stateChangeSupport
            );
            WorkflowDefinition definition = definition("""
                    {
                      "id": "http-failure-workflow",
                      "version": 1,
                      "nodes": [
                        { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                        { "id": "node_http", "name": "HTTP", "type": "HTTP", "params": { "url": "<<baseUrl>>/unavailable" } }
                      ],
                      "connections": {
                        "node_start": {
                          "main": [[{ "nodeId": "node_http", "type": "main", "index": 0 }]]
                        }
                      }
                    }
                    """);

            WorkflowExecution result = engine.run(executionService.create(definition, Map.of("baseUrl", server.baseUrl())));

            assertEquals(ExecutionStatus.FAILED, result.getStatus());
            assertEquals("HTTP request returned status 503: GET " + server.url("/unavailable"), result.getFailureMessage());
            assertEquals(server.url("/unavailable"), result.getContext().get("lastHttpUrl"));
            assertEquals(503, result.getContext().get("httpStatus"));

            List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
            assertEquals(2, nodeExecutions.size());
            assertEquals(ExecutionStatus.FAILED, nodeExecutions.get(1).getStatus());
            assertEquals("HTTP request returned status 503: GET " + server.url("/unavailable"), nodeExecutions.get(1).getMessage());
        }
    }

    @Test
    @DisplayName("12.AI 노드 실행 - messages 템플릿을 해석하고 mock 응답을 context에 기록한다")
    void aiExecutorResolvesMessagesAndStoresMockResponse() {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(
                new StartNodeExecutor(),
                new AINodeExecutor(),
                new EndNodeExecutor()
        ));
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "ai-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_ai", "name": "AI", "type": "AI", "params": {
                      "model": "gpt-4.1-mini",
                      "temperature": 0.2,
                      "messages": [
                        { "role": "system", "content": "You are a helper for <<customer.tier>> customers." },
                        { "role": "user", "content": "Summarize ticket <<ticket.title>>" }
                      ]
                    } },
                    { "id": "node_end", "name": "Done", "type": "END", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [[{ "nodeId": "node_ai", "type": "main", "index": 0 }]]
                    },
                    "node_ai": {
                      "main": [[{ "nodeId": "node_end", "type": "main", "index": 0 }]]
                    }
                  }
                }
                """);

        WorkflowExecution result = engine.run(executionService.create(definition, Map.of(
                "customer", Map.of("tier", "vip"),
                "ticket", Map.of("title", "Payment delayed")
        )));

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals(Map.of(
                "node_ai", Map.of(
                        "model", "gpt-4.1-mini",
                        "temperature", 0.2,
                        "imageCount", 0,
                        "response", "[gpt-4.1-mini] Summarize ticket Payment delayed (images=0)",
                        "prompt", List.of(
                                Map.of(
                                        "role", "system",
                                        "content", List.of(
                                                Map.of("type", "text", "text", "You are a helper for vip customers.")
                                        )
                                ),
                                Map.of(
                                        "role", "user",
                                        "content", List.of(
                                                Map.of("type", "text", "text", "Summarize ticket Payment delayed")
                                        )
                                )
                        )
                )
        ), result.getContext().get("aiOutputs"));
        assertEquals("node_end", result.getContext().get("handledBy"));
    }

    @Test
    @DisplayName("13.AI 멀티모달과 병렬 머지 - 노드별 결과를 aiOutputs에 분리해 저장한다")
    void aiExecutorStoresParallelOutputsByNodeId() {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(
                new StartNodeExecutor(),
                new AINodeExecutor(),
                new MergeNodeExecutor(),
                new EndNodeExecutor()
        ));
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "ai-parallel-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_ai_text", "name": "AI Text", "type": "AI", "params": {
                      "model": "gpt-4.1-mini",
                      "messages": [
                        { "role": "user", "content": "Summarize ticket <<ticket.title>>" }
                      ]
                    } },
                    { "id": "node_ai_vision", "name": "AI Vision", "type": "AI", "params": {
                      "model": "gpt-4.1-mini",
                      "messages": [
                        { "role": "user", "content": [
                          { "type": "text", "text": "Describe <<ticket.title>>" },
                          { "type": "image", "imageUrl": "https://img.example/<<ticket.imageId>>.png" }
                        ] }
                      ]
                    } },
                    { "id": "node_merge", "name": "Merge", "type": "MERGE", "params": {} },
                    { "id": "node_end", "name": "Done", "type": "END", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [[
                        { "nodeId": "node_ai_text", "type": "main", "index": 0 },
                        { "nodeId": "node_ai_vision", "type": "main", "index": 0 }
                      ]]
                    },
                    "node_ai_text": {
                      "main": [[{ "nodeId": "node_merge", "type": "main", "index": 0 }]]
                    },
                    "node_ai_vision": {
                      "main": [[{ "nodeId": "node_merge", "type": "main", "index": 1 }]]
                    },
                    "node_merge": {
                      "main": [[{ "nodeId": "node_end", "type": "main", "index": 0 }]]
                    }
                  }
                }
                """);

        WorkflowExecution result = engine.run(executionService.create(definition, Map.of(
                "ticket", Map.of("title", "Payment delayed", "imageId", "receipt-1")
        )));

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> aiOutputs = (Map<String, Object>) result.getContext().get("aiOutputs");
        assertEquals(Set.of("node_ai_text", "node_ai_vision"), aiOutputs.keySet());

        @SuppressWarnings("unchecked")
        Map<String, Object> textOutput = (Map<String, Object>) aiOutputs.get("node_ai_text");
        assertEquals("[gpt-4.1-mini] Summarize ticket Payment delayed (images=0)", textOutput.get("response"));
        assertEquals(0, textOutput.get("imageCount"));

        @SuppressWarnings("unchecked")
        Map<String, Object> visionOutput = (Map<String, Object>) aiOutputs.get("node_ai_vision");
        assertEquals("[gpt-4.1-mini] Describe Payment delayed (images=1)", visionOutput.get("response"));
        assertEquals(1, visionOutput.get("imageCount"));
        assertEquals(List.of(
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", "Describe Payment delayed"),
                                Map.of("type", "image", "imageUrl", "https://img.example/receipt-1.png")
                        )
                )
        ), visionOutput.get("prompt"));

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(Map.of(
                "aiOutputs", Map.of(
                        "node_ai_text", textOutput
                )
        ), nodeExecutions.stream()
                .filter(nodeExecution -> "node_ai_text".equals(nodeExecution.getNodeId()))
                .findFirst()
                .orElseThrow()
                .getOutput());
        assertEquals(Map.of(
                "aiOutputs", Map.of(
                        "node_ai_vision", visionOutput
                )
        ), nodeExecutions.stream()
                .filter(nodeExecution -> "node_ai_vision".equals(nodeExecution.getNodeId()))
                .findFirst()
                .orElseThrow()
                .getOutput());
    }

    @Test
    @DisplayName("14.AI 노드 입력 오류 - 잘못된 messages는 노드와 워크플로우를 FAILED로 만든다")
    void aiExecutorFailsWhenMessagesAreInvalid() {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(
                new StartNodeExecutor(),
                new AINodeExecutor()
        ));
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "ai-invalid-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_ai", "name": "AI", "type": "AI", "params": {
                      "messages": [
                        { "role": "user", "content": [] }
                      ]
                    } }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [[{ "nodeId": "node_ai", "type": "main", "index": 0 }]]
                    }
                  }
                }
                """);

        WorkflowExecution result = engine.run(executionService.create(definition, Map.of()));

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertEquals("AI node message content must be a non-empty string or parts array", result.getFailureMessage());

        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(result.getExecutionId());
        assertEquals(2, nodeExecutions.size());
        assertEquals(ExecutionStatus.FAILED, nodeExecutions.get(1).getStatus());
        assertEquals("AI node message content must be a non-empty string or parts array", nodeExecutions.get(1).getMessage());
    }

    @Test
    @DisplayName("13.observation - workflow와 node 상태 변경시 listener가 호출된다")
    void observationNotifiesWorkflowAndNodeListeners() throws Exception {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        RecordingWorkflowExecutionListener workflowListener = new RecordingWorkflowExecutionListener(3);
        RecordingNodeExecutionListener nodeListener = new RecordingNodeExecutionListener(4);
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(workflowListener),
                List.of(nodeListener),
                new SimpleAsyncTaskExecutor("observation-test-")
        );
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new EndNodeExecutor()));
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "observation-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_test", "name": "Observed", "type": "END", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [
                        [
                          { "nodeId": "node_test", "type": "main", "index": 0 }
                        ]
                      ]
                    }
                  }
                }
                """);

        WorkflowExecution execution = executionService.create(definition, Map.of());
        WorkflowExecution result = engine.run(execution);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertTrue(workflowListener.await());
        assertTrue(nodeListener.await());
        assertEquals(Set.of("ready", "started", "completed"), Set.copyOf(workflowListener.events()));
        assertEquals(Set.of(
                "node_start:started",
                "node_start:completed",
                "node_test:started",
                "node_test:completed"
        ), Set.copyOf(nodeListener.events()));
    }

    @Test
    @DisplayName("14.observation - listener 실패가 실행 흐름을 깨뜨리지 않는다")
    void observationIgnoresListenerFailures() throws Exception {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        RecordingWorkflowExecutionListener workflowListener = new RecordingWorkflowExecutionListener(3);
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(new FailingWorkflowExecutionListener(), workflowListener),
                List.of(),
                new SimpleAsyncTaskExecutor("observation-failure-test-")
        );
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new EndNodeExecutor()));
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SyncTaskExecutor()
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry),
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "observation-failure-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_test", "name": "Observed", "type": "END", "params": {} }
                  ],
                  "connections": {
                    "node_start": {
                      "main": [
                        [
                          { "nodeId": "node_test", "type": "main", "index": 0 }
                        ]
                      ]
                    }
                  }
                }
                """);

        WorkflowExecution execution = executionService.create(definition, Map.of());
        WorkflowExecution result = engine.run(execution);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertTrue(workflowListener.await());
        assertEquals(Set.of("ready", "started", "completed"), Set.copyOf(workflowListener.events()));
    }

    @Test
    @DisplayName("15.실행 중 중지 명령 - 중지 요청 시 현재 실행 중인 노드를 멈추고 실행 상태를 STOPPED로 전환한다")
    void stopMarksExecutionStoppedAndStopsCurrentAction() throws Exception {
        WorkflowExecutionRepository workflowExecutionRepository = new WorkflowExecutionRepository();
        NodeExecutionRepository nodeExecutionRepository = new NodeExecutionRepository();
        NodeExecutorRegistry nodeExecutorRegistry = new NodeExecutorRegistry(List.of(
                new StartNodeExecutor(),
                new TimerNodeExecutor(),
                new EndNodeExecutor(),
                new IfNodeExecutor(),
                new SwitchNodeExecutor(),
                new HttpNodeExecutor(),
                new AINodeExecutor(),
                new MergeNodeExecutor(),
                new LoopNodeExecutor(),
                new NoteNodeExecutor()
        ));
        WorkflowDefinitionService definitionService =
                new WorkflowDefinitionService(new WorkflowDefinitionRepository(), nodeExecutorRegistry);
        ExecutionStateChangeSupport stateChangeSupport = stateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                List.of(),
                List.of(),
                new SyncTaskExecutor()
        );
        WorkflowEngine engine = workflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new SimpleAsyncTaskExecutor("workflow-stop-test-")
        );
        WorkflowExecutionService executionService = workflowExecutionService(
                workflowExecutionRepository,
                definitionService,
                engine,
                stateChangeSupport
        );
        WorkflowDefinition definition = definition("""
                {
                  "id": "stoppable-workflow",
                  "version": 1,
                  "nodes": [
                    { "id": "node_start", "name": "Start", "type": "START", "params": {} },
                    { "id": "node_wait", "name": "Wait", "type": "TIMER", "params": {} },
                    { "id": "node_should_not_run", "name": "Should Not Run", "type": "END", "params": {} }
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
        definitionService.save(definition);

        WorkflowExecution started = executionService.start(definition.getId(), Map.of());
        assertNotNull(started.getExecutionId());

        awaitStatus(executionService, started.getExecutionId(), ExecutionStatus.RUNNING);
        awaitCurrentNode(executionService, started.getExecutionId(), "node_wait");

        WorkflowExecution stopping = executionService.stop(started.getExecutionId(), "manual stop");
        assertEquals(ExecutionStatus.STOPPING, stopping.getStatus());

        awaitStatus(executionService, started.getExecutionId(), ExecutionStatus.STOPPED);
        WorkflowExecution result = executionService.getRequired(started.getExecutionId());

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

    private TestHttpServer startHttpServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return new TestHttpServer(server);
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] response = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private String requestBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes());
    }

    private WorkflowExecutionService workflowExecutionService(WorkflowExecutionRepository repository,
                                                              WorkflowDefinitionService definitionService,
                                                              WorkflowEngine engine,
                                                              ExecutionStateChangeSupport stateChangeSupport) {
        return new WorkflowExecutionService(repository, definitionService, engine, stateChangeSupport);
    }

    private WorkflowEngine workflowEngine(NodeExecutorRegistry nodeExecutorRegistry,
                                          WorkflowExecutionRepository workflowExecutionRepository,
                                          ExecutionStateChangeSupport stateChangeSupport,
                                          org.springframework.core.task.TaskExecutor workflowTaskExecutor) {
        return new WorkflowEngine(
                nodeExecutorRegistry,
                workflowExecutionRepository,
                stateChangeSupport,
                new ContextTemplateResolver(),
                workflowTaskExecutor
        );
    }

    private ExecutionStateChangeSupport stateChangeSupport(WorkflowExecutionRepository workflowExecutionRepository,
                                                           NodeExecutionRepository nodeExecutionRepository,
                                                           List<WorkflowExecutionListener> workflowExecutionListeners,
                                                           List<NodeExecutionListener> nodeExecutionListeners,
                                                           org.springframework.core.task.TaskExecutor observationTaskExecutor) {
        return new ExecutionStateChangeSupport(
                workflowExecutionRepository,
                nodeExecutionRepository,
                new ExecutionObservationDispatcher(
                        workflowExecutionListeners,
                        nodeExecutionListeners,
                        observationTaskExecutor
                )
        );
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

    @FunctionalInterface
    private interface HttpHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        private String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        private String url(String pathAndQuery) {
            URI uri = URI.create(baseUrl() + pathAndQuery);
            return uri.toString();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class RecordingWorkflowExecutionListener implements WorkflowExecutionListener {
        private final CountDownLatch latch;
        private final List<String> events = new CopyOnWriteArrayList<>();

        private RecordingWorkflowExecutionListener(int expectedEvents) {
            this.latch = new CountDownLatch(expectedEvents);
        }

        @Override
        public void onWorkflowReady(WorkflowExecution execution) {
            events.add("ready");
            latch.countDown();
        }

        @Override
        public void onWorkflowStarted(WorkflowExecution execution) {
            events.add("started");
            latch.countDown();
        }

        @Override
        public void onWorkflowCompleted(WorkflowExecution execution) {
            events.add("completed");
            latch.countDown();
        }

        private boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class RecordingNodeExecutionListener implements NodeExecutionListener {
        private final CountDownLatch latch;
        private final List<String> events = new CopyOnWriteArrayList<>();

        private RecordingNodeExecutionListener(int expectedEvents) {
            this.latch = new CountDownLatch(expectedEvents);
        }

        @Override
        public void onNodeStarted(NodeExecution nodeExecution) {
            events.add(nodeExecution.getNodeId() + ":started");
            latch.countDown();
        }

        @Override
        public void onNodeCompleted(NodeExecution nodeExecution) {
            events.add(nodeExecution.getNodeId() + ":completed");
            latch.countDown();
        }

        private boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class FailingWorkflowExecutionListener implements WorkflowExecutionListener {
        @Override
        public void onWorkflowStarted(WorkflowExecution execution) {
            throw new IllegalStateException("listener failure");
        }
    }
}
