package com.luziatcode.demoworkflowengine.service.workflow;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import com.luziatcode.demoworkflowengine.service.workflow.engine.WorkflowEngine;
import com.luziatcode.demoworkflowengine.service.workflow.observation.ExecutionStateChangeSupport;
import com.luziatcode.demoworkflowengine.service.workflow.trigger.StartTriggerResolver;
import com.luziatcode.demoworkflowengine.service.workflow.trigger.StartTriggerType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
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
    private final ExecutionStateChangeSupport executionStateChangeSupport;
    private final StartTriggerResolver startTriggerResolver;

    public WorkflowExecutionService(WorkflowExecutionRepository repository,
                                    WorkflowDefinitionService workflowDefinitionService,
                                    WorkflowEngine workflowEngine,
                                    ExecutionStateChangeSupport executionStateChangeSupport) {
        this(repository, workflowDefinitionService, workflowEngine, executionStateChangeSupport, new StartTriggerResolver());
    }

    @Autowired
    public WorkflowExecutionService(WorkflowExecutionRepository repository,
                                    WorkflowDefinitionService workflowDefinitionService,
                                    WorkflowEngine workflowEngine,
                                    ExecutionStateChangeSupport executionStateChangeSupport,
                                    StartTriggerResolver startTriggerResolver) {
        this.repository = repository;
        this.workflowDefinitionService = workflowDefinitionService;
        this.workflowEngine = workflowEngine;
        this.executionStateChangeSupport = executionStateChangeSupport;
        this.startTriggerResolver = startTriggerResolver;
    }

    /**
     * workflow id/version, input var 저장.
     */
    public WorkflowExecution create(WorkflowDefinition definition, Map<String, Object> initContext) {
        WorkflowExecution execution = new WorkflowExecution();

        execution.setExecutionId(UUID.randomUUID().toString());
        execution.setStatus(ExecutionStatus.READY);
        execution.setContext(initContext);

        execution.setWorkflowId(definition.getId());
        execution.setWorkflowVersion(definition.getVersion());
        execution.setDefinition(definition);
        execution.setCreatedAt(ZonedDateTime.now());
        execution.setUpdatedAt(ZonedDateTime.now());

        return executionStateChangeSupport.saveNewWorkflowExecution(execution);
    }

    public WorkflowExecution update(WorkflowExecution execution) {
        return executionStateChangeSupport.saveWorkflowExecution(execution);
    }

    public WorkflowExecution start(String workflowId, Map<String, Object> input) {
        WorkflowDefinition definition = workflowDefinitionService.getLatest(workflowId);
        return start(definition, startTriggerResolver.buildTriggerContext(definition, StartTriggerType.MANUAL, input));
    }

    public WorkflowExecution start(WorkflowDefinition definition, Map<String, Object> input) {
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
        execution.setFailureMessage(null);
        return executionStateChangeSupport.transitionWorkflowStatus(execution, ExecutionStatus.STOPPING);
    }
}
