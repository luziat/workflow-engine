package com.luziatcode.demoworkflowengine.service.workflow.domain.execution;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
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
        this.input = copyMap(input);
    }

    public void setOutput(Map<String, Object> output) {
        this.output = copyMap(output);
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
