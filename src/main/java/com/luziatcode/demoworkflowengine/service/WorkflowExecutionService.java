package com.luziatcode.demoworkflowengine.service;

import com.luziatcode.demoworkflowengine.domain.ExecutionStatus;
import com.luziatcode.demoworkflowengine.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.domain.WorkflowExecution;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowExecutionService {
    private final WorkflowExecutionRepository repository;

    public WorkflowExecutionService(WorkflowExecutionRepository repository) {
        this.repository = repository;
    }

    public WorkflowExecution create(WorkflowDefinition definition, Map<String, Object> input) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setExecutionId(UUID.randomUUID().toString());
        execution.setWorkflowId(definition.getId());
        execution.setWorkflowVersion(definition.getVersion());
        execution.setStatus(ExecutionStatus.READY);
        execution.setCreatedAt(Instant.now());
        execution.setUpdatedAt(Instant.now());
        execution.setContext(input != null ? new LinkedHashMap<>(input) : new LinkedHashMap<>());
        return repository.save(execution);
    }

    public WorkflowExecution update(WorkflowExecution execution) {
        execution.setUpdatedAt(Instant.now());
        return repository.save(execution);
    }

    public WorkflowExecution getRequired(String executionId) {
        return repository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
    }
}
