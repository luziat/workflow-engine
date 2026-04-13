package com.luziatcode.demoworkflowengine.service.workflow.listener;

import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import com.luziatcode.demoworkflowengine.service.workflow.observation.WorkflowExecutionListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingWorkflowExecutionListener implements WorkflowExecutionListener {

    @Override
    public void onWorkflowReady(WorkflowExecution execution) {
        log.info("Workflow ready. executionId={}, definitionId={}", execution.getExecutionId(), execution.getDefinitionId());
    }

    @Override
    public void onWorkflowStarted(WorkflowExecution execution) {
        log.info("Workflow started. executionId={}, definitionId={}", execution.getExecutionId(), execution.getDefinitionId());
    }

    @Override
    public void onWorkflowStopping(WorkflowExecution execution) {
        log.info("Workflow stopping. executionId={}, definitionId={}", execution.getExecutionId(), execution.getDefinitionId());
    }

    @Override
    public void onWorkflowStopped(WorkflowExecution execution) {
        log.info("Workflow stopped. executionId={}, definitionId={}", execution.getExecutionId(), execution.getDefinitionId());
    }

    @Override
    public void onWorkflowCompleted(WorkflowExecution execution) {
        log.info("Workflow completed. executionId={}, definitionId={}", execution.getExecutionId(), execution.getDefinitionId());
    }

    @Override
    public void onWorkflowFailed(WorkflowExecution execution) {
        log.info("Workflow failed. executionId={}, definitionId={}, message={}",
                execution.getExecutionId(),
                execution.getDefinitionId(),
                execution.getFailureMessage());
    }

    @Override
    public void onWorkflowCancelled(WorkflowExecution execution) {
        log.info("Workflow cancelled. executionId={}, definitionId={}", execution.getExecutionId(), execution.getDefinitionId());
    }
}
