package com.luziatcode.demoworkflowengine.service.workflow.trigger;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class StartTriggerResolver {
    private static final String TRIGGER_TYPE_KEY = "triggerType";
    private static final String ENABLED_KEY = "enabled";
    private static final String CRON_KEY = "cron";
    private static final String WEBHOOK_TOKEN_KEY = "webhookToken";
    private static final String INPUT_KEY = "input";

    public StartTrigger resolve(WorkflowDefinition definition) {
        return resolve(getRequiredStartNode(definition));
    }

    public StartTrigger resolve(Node startNode) {
        Map<String, Object> metadata = startNode.getMetadata() == null ? Map.of() : startNode.getMetadata();
        StartTriggerType type = resolveTriggerType(metadata.get(TRIGGER_TYPE_KEY));
        boolean enabled = resolveEnabled(metadata.get(ENABLED_KEY));
        String cron = textOrNull(metadata.get(CRON_KEY));
        String webhookToken = textOrNull(metadata.get(WEBHOOK_TOKEN_KEY));
        Map<String, Object> input = resolveInput(metadata.get(INPUT_KEY));
        return new StartTrigger(type, enabled, cron, webhookToken, input);
    }

    public void validate(Node startNode) {
        StartTrigger trigger = resolve(startNode);
        if (trigger.type() == StartTriggerType.CRON) {
            requireText(trigger.cron(), "START cron trigger requires cron metadata");
            try {
                CronExpression.parse(trigger.cron());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid START cron expression: " + trigger.cron(), exception);
            }
        }
        if (trigger.type() == StartTriggerType.WEBHOOK) {
            requireText(trigger.webhookToken(), "START webhook trigger requires webhookToken metadata");
        }
    }

    public Node getRequiredStartNode(WorkflowDefinition definition) {
        return definition.getNodes().stream()
                .filter(node -> NodeType.START.equals(node.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Exactly one start node is required"));
    }

    public Map<String, Object> buildTriggerContext(
            WorkflowDefinition definition,
            StartTriggerType actualTriggerType,
            Map<String, Object> baseInput
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (baseInput != null) {
            merged.putAll(baseInput);
        }

        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", actualTriggerType.name().toLowerCase(Locale.ROOT));
        trigger.put("workflowId", definition.getId());
        trigger.put("workflowVersion", definition.getVersion());
        trigger.put("triggeredAt", ZonedDateTime.now().toString());
        merged.put("trigger", trigger);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveInput(Object rawInput) {
        if (rawInput == null) {
            return Map.of();
        }
        if (!(rawInput instanceof Map<?, ?> inputMap)) {
            throw new IllegalArgumentException("START trigger input metadata must be an object");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private StartTriggerType resolveTriggerType(Object rawTriggerType) {
        if (rawTriggerType == null) {
            return StartTriggerType.MANUAL;
        }

        String triggerType = requireText(rawTriggerType, "START triggerType must not be blank");
        try {
            return StartTriggerType.valueOf(triggerType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported START triggerType: " + triggerType, exception);
        }
    }

    private boolean resolveEnabled(Object rawEnabled) {
        if (rawEnabled == null) {
            return true;
        }
        if (rawEnabled instanceof Boolean enabled) {
            return enabled;
        }
        return Boolean.parseBoolean(String.valueOf(rawEnabled));
    }

    private String textOrNull(Object value) {
        if (value == null) {
            return null;
        }
        return requireText(value, "START trigger metadata value must not be blank");
    }

    private String requireText(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }
}
