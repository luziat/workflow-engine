package com.luziatcode.demoworkflowengine.service.workflow.engine;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class NodeResult {
    private final ExecutionDirective directive;
    private final Map<String, Object> output;
    private final String message;

    private NodeResult(ExecutionDirective directive, Map<String, Object> output, String message) {
        this.directive = directive;
        this.output = output != null ? new LinkedHashMap<>(output) : new LinkedHashMap<>();
        this.message = message;
    }

    public static NodeResult next(Map<String, Object> output) {
        return new NodeResult(ExecutionDirective.NEXT, output, null);
    }

    public static NodeResult finish(Map<String, Object> output) {
        return new NodeResult(ExecutionDirective.FINISH, output, null);
    }
}
