package com.luziatcode.demoworkflowengine.dto;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;

import java.time.ZonedDateTime;
import java.util.Map;

public record WorkflowExecutionResponse(
        String executionId,
        String definitionId,
        int definitionVersion,
        ExecutionStatus status,
        String currentNodeId,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        String failureMessage,
        Map<String, Object> context
) {
    public static WorkflowExecutionResponse from(WorkflowExecution execution) {
        return new WorkflowExecutionResponse(
                execution.getExecutionId(),
                execution.getDefinitionId(),
                execution.getDefinitionVersion(),
                execution.getStatus(),
                execution.getCurrentNodeId(),
                execution.getCreatedAt(),
                execution.getUpdatedAt(),
                execution.getFailureMessage(),
                execution.getContext()
        );
    }
}
