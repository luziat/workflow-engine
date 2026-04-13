package com.luziatcode.demoworkflowengine.repository;

import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class WorkflowExecutionRepository {
    private final Map<String, WorkflowExecution> store = new ConcurrentHashMap<>();

    public WorkflowExecution save(WorkflowExecution execution) {
        store.put(execution.getExecutionId(), execution);
        return execution;
    }

    public Optional<WorkflowExecution> findById(String executionId) {
        return Optional.ofNullable(store.get(executionId));
    }
}
