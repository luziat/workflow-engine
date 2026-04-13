package com.luziatcode.demoworkflowengine.service.workflow.engine;

import com.luziatcode.demoworkflowengine.service.workflow.domain.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NodeExecutorRegistry {
    private final List<NodeExecutor> executors;
    private Map<NodeType, NodeExecutor> executorsByType;

    public NodeExecutor getRequired(NodeType type) {
        if (executorsByType == null) {
            executorsByType = executors.stream().collect(Collectors.toMap(NodeExecutor::getType, Function.identity()));
        }
        NodeExecutor executor = executorsByType.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("Unsupported node type: " + type);
        }
        return executor;
    }

    public Set<NodeType> supportedTypes() {
        if (executorsByType == null) {
            executorsByType = executors.stream().collect(Collectors.toMap(NodeExecutor::getType, Function.identity()));
        }
        return executorsByType.keySet();
    }
}
