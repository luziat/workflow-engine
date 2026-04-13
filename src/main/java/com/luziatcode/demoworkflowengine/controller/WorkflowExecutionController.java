package com.luziatcode.demoworkflowengine.controller;

import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import com.luziatcode.demoworkflowengine.service.workflow.engine.WorkflowEngine;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.service.WorkflowDefinitionService;
import com.luziatcode.demoworkflowengine.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class WorkflowExecutionController {
    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowEngine workflowEngine;
    private final NodeExecutionRepository nodeExecutionRepository;

    @PostMapping("/{workflowId}")
    public WorkflowExecution start(@PathVariable String workflowId,
                                   @RequestBody(required = false) Map<String, Object> input) {
        WorkflowDefinition definition = workflowDefinitionService.getLatest(workflowId);
        WorkflowExecution workflowExecution = workflowExecutionService.create(definition, input);
        return workflowEngine.run(workflowExecution);
    }

    @PostMapping("/{executionId}/stop")
    public WorkflowExecution stop(@PathVariable String executionId,
                                  @RequestBody(required = false) StopRequest request) {
        String reason = request != null && request.reason() != null && !request.reason().isBlank()
                ? request.reason()
                : "Stopped by user request";
        return workflowEngine.stop(executionId, reason);
    }

    @GetMapping("/{executionId}")
    public WorkflowExecution get(@PathVariable String executionId) {
        return workflowExecutionService.getRequired(executionId);
    }

    @GetMapping("/{executionId}/nodes")
    public List<?> getNodeExecutions(@PathVariable String executionId) {
        return nodeExecutionRepository.findByExecutionId(executionId);
    }

    private record StopRequest(String reason) {
    }
}
