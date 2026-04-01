package com.luziatcode.demoworkflowengine.service.workflow.engine;

import com.luziatcode.demoworkflowengine.service.workflow.task.NodeTask;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NodeExecutorRegistry {
    private final Map<String, NodeTask> executors;

    public NodeExecutorRegistry(List<NodeTask> executors) {
        this.executors = executors.stream()
                .collect(Collectors.toMap(NodeTask::getType, Function.identity()));
    }

    public NodeTask getRequired(String type) {
        NodeTask executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("Unsupported node type: " + type);
        }
        return executor;
    }
}
