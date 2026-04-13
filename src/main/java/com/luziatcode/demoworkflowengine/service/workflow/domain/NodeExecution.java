package com.luziatcode.demoworkflowengine.service.workflow.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ToString
public class NodeExecution {
    private String executionId;
    private String nodeId;
    private ExecutionStatus status;
    private ZonedDateTime startedAt = ZonedDateTime.now();
    private ZonedDateTime endedAt;
    private Map<String, Object> input = new LinkedHashMap<>();
    private Map<String, Object> output = new LinkedHashMap<>();
    private String message;

    public void setInput(Map<String, Object> input) {
        this.input = input != null ? new LinkedHashMap<>(input) : new LinkedHashMap<>();
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output != null ? new LinkedHashMap<>(output) : new LinkedHashMap<>();
    }
}
