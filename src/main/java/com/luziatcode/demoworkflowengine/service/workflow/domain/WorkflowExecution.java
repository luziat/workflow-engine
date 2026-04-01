package com.luziatcode.demoworkflowengine.service.workflow.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ToString
public class WorkflowExecution {
    private String executionId;
    private String workflowId;
    private int workflowVersion;
    private ExecutionStatus status = ExecutionStatus.READY;
    private String currentNodeId;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private String failureMessage;
    private Map<String, Object> context = new LinkedHashMap<>();

    public void setContext(Map<String, Object> context) {
        this.context = context != null ? new LinkedHashMap<>(context) : new LinkedHashMap<>();
    }
}
