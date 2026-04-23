package com.luziatcode.demoworkflowengine.service.workflow.trigger;

import java.util.LinkedHashMap;
import java.util.Map;

public record StartTrigger(
        StartTriggerType type,
        boolean enabled,
        String cron,
        String webhookToken,
        Map<String, Object> input
) {
    public Map<String, Object> inputOrEmpty() {
        return input == null ? Map.of() : new LinkedHashMap<>(input);
    }
}
