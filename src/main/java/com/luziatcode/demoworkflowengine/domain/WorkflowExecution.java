package com.luziatcode.demoworkflowengine.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkflowExecution {
    private String executionId;
    private String workflowId;
    private int workflowVersion;
    private ExecutionStatus status = ExecutionStatus.READY;
    private String currentNodeId;
    private String waitingNodeId;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private String failureMessage;
    private Map<String, Object> context = new LinkedHashMap<>();

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public int getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(int workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getWaitingNodeId() {
        return waitingNodeId;
    }

    public void setWaitingNodeId(String waitingNodeId) {
        this.waitingNodeId = waitingNodeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context != null ? new LinkedHashMap<>(context) : new LinkedHashMap<>();
    }
}
