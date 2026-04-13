package com.luziatcode.demoworkflowengine.repository;

import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.NodeExecution;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class NodeExecutionRepository {
    private final Map<String, List<NodeExecution>> store = new ConcurrentHashMap<>();

    public NodeExecution save(NodeExecution nodeExecution) {
        store.computeIfAbsent(nodeExecution.getExecutionId(), key -> new ArrayList<>()).add(nodeExecution);
        return nodeExecution;
    }

    public List<NodeExecution> findByExecutionId(String executionId) {
        return new ArrayList<>(store.getOrDefault(executionId, List.of()));
    }
}
