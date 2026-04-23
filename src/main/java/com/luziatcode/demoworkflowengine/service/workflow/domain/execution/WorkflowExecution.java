package com.luziatcode.demoworkflowengine.service.workflow.domain.execution;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
public class WorkflowExecution {
    private String executionId;
    private String workflowId;
    private int workflowVersion;
    private WorkflowDefinition definition;
    private ExecutionStatus status = ExecutionStatus.READY;
    private String currentNodeId;
    private ZonedDateTime createdAt = ZonedDateTime.now();
    private ZonedDateTime startedAt;
    private ZonedDateTime updatedAt = ZonedDateTime.now();
    private ZonedDateTime endedAt;
    private String failureMessage;
    private Map<String, Object> context = new LinkedHashMap<>();

    public void setContext(Map<String, Object> context) {
        this.context = copyMap(context);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Object copyValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return copyMap((Map<String, Object>) mapValue);
        }
        if (value instanceof List<?> listValue) {
            List<Object> copy = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }
}
