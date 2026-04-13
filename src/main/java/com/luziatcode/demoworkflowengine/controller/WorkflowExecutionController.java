package com.luziatcode.demoworkflowengine.controller;

import com.luziatcode.demoworkflowengine.dto.StopRequest;
import com.luziatcode.demoworkflowengine.dto.WorkflowExecutionResponse;
import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.service.workflow.WorkflowExecutionService;
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
    private final WorkflowExecutionService workflowExecutionService;
    private final NodeExecutionRepository nodeExecutionRepository;

    @PostMapping("/{workflowId}/start")
    public WorkflowExecutionResponse start(@PathVariable String workflowId,
                                           @RequestBody(required = false) Map<String, Object> input) {
        return WorkflowExecutionResponse.from(workflowExecutionService.start(workflowId, input));
    }

    @PostMapping("/{executionId}/stop")
    public WorkflowExecutionResponse stop(@PathVariable String executionId,
                                          @RequestBody(required = false) StopRequest request) {
        String reason = request != null && request.reason() != null && !request.reason().isBlank()
                ? request.reason()
                : "Stopped by user request";
        return WorkflowExecutionResponse.from(workflowExecutionService.stop(executionId, reason));
    }

    @GetMapping("/{executionId}")
    public WorkflowExecutionResponse get(@PathVariable String executionId) {
        return WorkflowExecutionResponse.from(workflowExecutionService.getRequired(executionId));
    }

    @GetMapping("/{executionId}/nodes")
    public List<?> getNodeExecutions(@PathVariable String executionId) {
        return nodeExecutionRepository.findByExecutionId(executionId);
    }
}
