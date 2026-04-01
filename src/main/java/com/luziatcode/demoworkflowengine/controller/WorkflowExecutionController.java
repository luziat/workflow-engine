package com.luziatcode.demoworkflowengine.controller;

import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowExecution;
import com.luziatcode.demoworkflowengine.service.workflow.engine.WorkflowEngine;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.service.WorkflowDefinitionService;
import com.luziatcode.demoworkflowengine.service.WorkflowExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/executions")
public class WorkflowExecutionController {
    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowEngine workflowEngine;
    private final NodeExecutionRepository nodeExecutionRepository;

    public WorkflowExecutionController(WorkflowDefinitionService workflowDefinitionService,
                                       WorkflowExecutionService workflowExecutionService,
                                       WorkflowEngine workflowEngine,
                                       NodeExecutionRepository nodeExecutionRepository) {
        this.workflowDefinitionService = workflowDefinitionService;
        this.workflowExecutionService = workflowExecutionService;
        this.workflowEngine = workflowEngine;
        this.nodeExecutionRepository = nodeExecutionRepository;
    }

    @PostMapping("/{workflowId}")
    public WorkflowExecution start(@PathVariable String workflowId,
                                   @RequestBody(required = false) Map<String, Object> input) {
        WorkflowDefinition definition = workflowDefinitionService.getLatest(workflowId);
        WorkflowExecution execution = workflowExecutionService.create(definition, input);
        return workflowEngine.run(definition, execution);
    }

    @PostMapping("/{executionId}/resume")
    public WorkflowExecution resume(@PathVariable String executionId,
                                    @RequestBody(required = false) Map<String, Object> input) {
        WorkflowExecution execution = workflowExecutionService.getRequired(executionId);
        WorkflowDefinition definition = workflowDefinitionService.getRequired(execution.getWorkflowId(), execution.getWorkflowVersion());
        if (input != null) {
            execution.getContext().putAll(input);
        }
        execution.setWaitingNodeId(null);
        return workflowEngine.run(definition, workflowExecutionService.update(execution));
    }

    @GetMapping("/{executionId}")
    public WorkflowExecution get(@PathVariable String executionId) {
        return workflowExecutionService.getRequired(executionId);
    }

    @GetMapping("/{executionId}/nodes")
    public List<?> getNodeExecutions(@PathVariable String executionId) {
        return nodeExecutionRepository.findByExecutionId(executionId);
    }
}
