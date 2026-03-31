package com.luziatcode.demoworkflowengine.engine;

import com.luziatcode.demoworkflowengine.executor.NodeExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NodeExecutorRegistry {
    private final Map<String, NodeExecutor> executors;

    public NodeExecutorRegistry(List<NodeExecutor> executors) {
        this.executors = executors.stream()
                .collect(Collectors.toMap(NodeExecutor::getType, Function.identity()));
    }

    public NodeExecutor getRequired(String type) {
        NodeExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("Unsupported node type: " + type);
        }
        return executor;
    }
}
