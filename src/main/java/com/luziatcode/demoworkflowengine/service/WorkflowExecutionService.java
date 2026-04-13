package com.luziatcode.demoworkflowengine.service;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import com.luziatcode.demoworkflowengine.service.workflow.engine.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
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
    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowEngine workflowEngine;

    /**
     * definition id, input var 저장.
     */
    public WorkflowExecution create(WorkflowDefinition definition, Map<String, Object> initContext) {
        WorkflowExecution execution = new WorkflowExecution();

        execution.setExecutionId(UUID.randomUUID().toString());
        execution.setStatus(ExecutionStatus.READY);
        execution.setContext(initContext);

        execution.setDefinitionId(definition.getId());
        execution.setDefinitionVersion(definition.getVersion());
        execution.setDefinition(definition);

        execution.setCreatedAt(ZonedDateTime.now());
        execution.setUpdatedAt(ZonedDateTime.now());

        return repository.save(execution);
    }

    public WorkflowExecution update(WorkflowExecution execution) {
        execution.setUpdatedAt(ZonedDateTime.now());
        return repository.save(execution);
    }

    public WorkflowExecution start(String workflowId, Map<String, Object> input) {
        WorkflowDefinition definition = workflowDefinitionService.getLatest(workflowId);
        WorkflowExecution execution = create(definition, input);
        return workflowEngine.runAsync(execution);
    }

    public WorkflowExecution getRequired(String executionId) {
        return repository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
    }

    public WorkflowExecution stop(String executionId, String reason) {
        return workflowEngine.stop(executionId, reason);
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
