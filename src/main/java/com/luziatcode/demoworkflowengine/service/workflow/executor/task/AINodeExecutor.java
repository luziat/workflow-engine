package com.luziatcode.demoworkflowengine.service.workflow.executor.task;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * messages 기반 mock LLM 응답을 생성해 workflow context 에 기록하는 executor.
 *
 * <p>params 예시:
 * <pre>{@code
 * {
 *   "model": "gpt-4.1-mini",
 *   "temperature": 0.2,
 *   "messages": [
 *     { "role": "system", "content": "You are a helper." },
 *     {
 *       "role": "user",
 *       "content": [
 *         { "type": "text", "text": "Describe this image" },
 *         { "type": "image", "imageUrl": "https://example.com/image.png" }
 *       ]
 *     }
 *   ]
 * }
 * }</pre>
 */
@Component
public class AINodeExecutor implements NodeExecutor {
    private static final String DEFAULT_MODEL = "mock-ai-model";
    private static final Set<String> ALLOWED_ROLES = Set.of("system", "user", "assistant");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("text", "image");
    private static final String AI_OUTPUTS_KEY = "aiOutputs";

    @Override
    public NodeType getType() {
        return NodeType.AI;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        List<Map<String, Object>> messages = normalizeMessages(context.resolvedParams().get("messages"));
        String model = String.valueOf(context.resolvedParams().getOrDefault("model", DEFAULT_MODEL));
        Object temperature = context.resolvedParams().get("temperature");
        int imageCount = countImages(messages);

        Map<String, Object> nodeOutput = new LinkedHashMap<>();
        nodeOutput.put("prompt", List.copyOf(messages));
        nodeOutput.put("model", model);
        nodeOutput.put("temperature", temperature);
        nodeOutput.put("imageCount", imageCount);
        nodeOutput.put("response", buildMockResponse(messages, model, imageCount));

        @SuppressWarnings("unchecked")
        Map<String, Object> aiOutputs = (Map<String, Object>) context.execution()
                .getContext()
                .computeIfAbsent(AI_OUTPUTS_KEY, key -> new LinkedHashMap<String, Object>());
        aiOutputs.put(context.node().getId(), nodeOutput);
    }

    private List<Map<String, Object>> normalizeMessages(Object rawMessages) {
        if (!(rawMessages instanceof List<?> messages) || messages.isEmpty()) {
            throw new IllegalArgumentException("AI node requires a non-empty messages array");
        }

        List<Map<String, Object>> normalized = new ArrayList<>(messages.size());
        for (Object rawMessage : messages) {
            if (!(rawMessage instanceof Map<?, ?> messageMap)) {
                throw new IllegalArgumentException("AI node message must be an object");
            }

            String role = requireText(messageMap.get("role"), "AI node message role is required")
                    .toLowerCase(Locale.ROOT);
            if (!ALLOWED_ROLES.contains(role)) {
                throw new IllegalArgumentException("Unsupported AI message role: " + role);
            }

            Map<String, Object> normalizedMessage = new LinkedHashMap<>();
            normalizedMessage.put("role", role);
            normalizedMessage.put("content", normalizeContent(messageMap.get("content")));
            normalized.add(normalizedMessage);
        }

        return normalized;
    }

    private List<Map<String, Object>> normalizeContent(Object rawContent) {
        if (rawContent instanceof String text) {
            return List.of(textPart(requireText(text, "AI node message content is required")));
        }
        if (!(rawContent instanceof List<?> contentParts) || contentParts.isEmpty()) {
            throw new IllegalArgumentException("AI node message content must be a non-empty string or parts array");
        }

        List<Map<String, Object>> normalized = new ArrayList<>(contentParts.size());
        for (Object rawPart : contentParts) {
            if (!(rawPart instanceof Map<?, ?> partMap)) {
                throw new IllegalArgumentException("AI node content part must be an object");
            }

            String type = requireText(partMap.get("type"), "AI node content part type is required")
                    .toLowerCase(Locale.ROOT);
            if (!ALLOWED_CONTENT_TYPES.contains(type)) {
                throw new IllegalArgumentException("Unsupported AI content part type: " + type);
            }

            if ("text".equals(type)) {
                normalized.add(textPart(requireText(partMap.get("text"), "AI node text part requires text")));
                continue;
            }

            Map<String, Object> imagePart = new LinkedHashMap<>();
            imagePart.put("type", "image");
            imagePart.put("imageUrl", requireText(partMap.get("imageUrl"), "AI node image part requires imageUrl"));
            normalized.add(imagePart);
        }
        return normalized;
    }

    private Map<String, Object> textPart(String text) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "text");
        part.put("text", text);
        return part;
    }

    private String requireText(Object value, String errorMessage) {
        if (value == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return text;
    }

    private int countImages(List<Map<String, Object>> messages) {
        int imageCount = 0;
        for (Map<String, Object> message : messages) {
            for (Map<String, Object> part : contentParts(message)) {
                if (Objects.equals("image", part.get("type"))) {
                    imageCount++;
                }
            }
        }
        return imageCount;
    }

    private String buildMockResponse(List<Map<String, Object>> messages, String model, int imageCount) {
        return "[" + model + "] " + extractPreferredPrompt(messages) + " (images=" + imageCount + ")";
    }

    private String extractPreferredPrompt(List<Map<String, Object>> messages) {
        List<Map<String, Object>> selected = null;
        for (Map<String, Object> message : messages) {
            if ("user".equals(message.get("role"))) {
                selected = contentParts(message);
            }
        }
        if (selected == null) {
            selected = contentParts(messages.get(messages.size() - 1));
        }

        List<String> texts = new ArrayList<>();
        for (Map<String, Object> part : selected) {
            if (Objects.equals("text", part.get("type"))) {
                texts.add(String.valueOf(part.get("text")));
            }
        }
        if (texts.isEmpty()) {
            return "Image-only prompt";
        }
        return String.join(" ", texts);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> contentParts(Map<String, Object> message) {
        Object content = message.get("content");
        if (content instanceof Collection<?>) {
            return (List<Map<String, Object>>) content;
        }
        throw new IllegalArgumentException("AI node message content must be normalized before use");
    }
}
