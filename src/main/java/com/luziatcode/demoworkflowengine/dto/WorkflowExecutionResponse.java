package com.luziatcode.demoworkflowengine.dto;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;

import java.time.ZonedDateTime;
import java.util.Map;

public record WorkflowExecutionResponse(
        String executionId,
        String workflowId,
        int workflowVersion,
        ExecutionStatus status,
        String currentNodeId,
        ZonedDateTime createdAt,
        ZonedDateTime startedAt,
        ZonedDateTime updatedAt,
        ZonedDateTime endedAt,
        String failureMessage,
        Map<String, Object> context
) {
    public static WorkflowExecutionResponse from(WorkflowExecution execution) {
        return new WorkflowExecutionResponse(
                execution.getExecutionId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersion(),
                execution.getStatus(),
                execution.getCurrentNodeId(),
                execution.getCreatedAt(),
                execution.getStartedAt(),
                execution.getUpdatedAt(),
                execution.getEndedAt(),
                execution.getFailureMessage(),
                execution.getContext()
        );
    }
}
