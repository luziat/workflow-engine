package com.luziatcode.demoworkflowengine.service;

import com.luziatcode.demoworkflowengine.service.workflow.domain.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowExecution;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowExecutionService {
    private static final Set<ExecutionStatus> TERMINAL_STATUSES = Set.of(
            ExecutionStatus.SUCCESS,
            ExecutionStatus.FAILED,
            ExecutionStatus.STOPPED,
            ExecutionStatus.CANCELLED
    );

    private final WorkflowExecutionRepository repository;

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

    public WorkflowExecution markStopping(String executionId) {
        WorkflowExecution execution = getRequired(executionId);
        if (TERMINAL_STATUSES.contains(execution.getStatus())) {
            return execution;
        }
        execution.setStatus(ExecutionStatus.STOPPING);
        execution.setFailureMessage(null);
        return update(execution);
    }
}
