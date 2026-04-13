package com.luziatcode.demoworkflowengine.service.workflow.domain.execution;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ToString
public class WorkflowExecution {
    private String executionId;
    private String definitionId;
    private int definitionVersion;
    private WorkflowDefinition definition;
    private ExecutionStatus status = ExecutionStatus.READY;
    private String currentNodeId;
    private ZonedDateTime createdAt = ZonedDateTime.now();
    private ZonedDateTime updatedAt = ZonedDateTime.now();
    private String failureMessage;
    private Map<String, Object> context = new LinkedHashMap<>();

    public void setContext(Map<String, Object> context) {
        this.context = context != null ? new LinkedHashMap<>(context) : new LinkedHashMap<>();
    }
}
