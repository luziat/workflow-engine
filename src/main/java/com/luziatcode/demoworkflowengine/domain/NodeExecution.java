package com.luziatcode.demoworkflowengine.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class NodeExecution {
    private String executionId;
    private String nodeId;
    private ExecutionStatus status;
    private Instant startedAt = Instant.now();
    private Instant endedAt;
    private Map<String, Object> input = new LinkedHashMap<>();
    private Map<String, Object> output = new LinkedHashMap<>();
    private String message;

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input != null ? new LinkedHashMap<>(input) : new LinkedHashMap<>();
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output != null ? new LinkedHashMap<>(output) : new LinkedHashMap<>();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
