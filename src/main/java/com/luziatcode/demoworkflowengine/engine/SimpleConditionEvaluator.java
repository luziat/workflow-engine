package com.luziatcode.demoworkflowengine.engine;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SimpleConditionEvaluator {

    public boolean matches(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        String trimmed = expression.trim();
        if (trimmed.contains(">=")) {
            String[] parts = trimmed.split(">=");
            return readNumber(parts[0], context) >= Double.parseDouble(parts[1].trim());
        }
        if (trimmed.contains("<=")) {
            String[] parts = trimmed.split("<=");
            return readNumber(parts[0], context) <= Double.parseDouble(parts[1].trim());
        }
        if (trimmed.contains("==")) {
            String[] parts = trimmed.split("==");
            return String.valueOf(context.get(parts[0].trim())).equals(parts[1].trim());
        }
        if (trimmed.contains(">")) {
            String[] parts = trimmed.split(">");
            return readNumber(parts[0], context) > Double.parseDouble(parts[1].trim());
        }
        if (trimmed.contains("<")) {
            String[] parts = trimmed.split("<");
            return readNumber(parts[0], context) < Double.parseDouble(parts[1].trim());
        }
        throw new IllegalArgumentException("Unsupported condition expression: " + expression);
    }

    private double readNumber(String key, Map<String, Object> context) {
        Object value = context.get(key.trim());
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
