package com.luziatcode.demoworkflowengine.service.workflow.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ContextTemplateResolver {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    public Map<String, Object> resolveParams(Map<String, Object> params, Map<String, Object> context) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue(), context));
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value, Map<String, Object> context) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> resolvedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                resolvedMap.put(String.valueOf(entry.getKey()), resolveValue(entry.getValue(), context));
            }
            return resolvedMap;
        }
        if (value instanceof List<?> listValue) {
            List<Object> resolvedList = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                resolvedList.add(resolveValue(item, context));
            }
            return resolvedList;
        }
        if (value instanceof String text) {
            return resolveString(text, context);
        }
        return value;
    }

    private String resolveString(String template, Map<String, Object> context) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuilder resolved = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            resolved.append(template, cursor, matcher.start());
            resolved.append(resolvePath(matcher.group(1), context));
            cursor = matcher.end();
        }

        if (cursor == 0) {
            return template;
        }

        resolved.append(template.substring(cursor));
        return resolved.toString();
    }

    @SuppressWarnings("unchecked")
    private String resolvePath(String rawPath, Map<String, Object> context) {
        Object current = context;
        for (String segment : rawPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> currentMap) || !currentMap.containsKey(segment)) {
                throw new IllegalArgumentException("Template variable not found: " + rawPath);
            }
            current = ((Map<String, Object>) currentMap).get(segment);
        }
        if (current == null) {
            throw new IllegalArgumentException("Template variable not found: " + rawPath);
        }
        return String.valueOf(current);
    }
}
