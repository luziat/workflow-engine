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
        log.info("Workflow ready. executionId={}, workflowId={}", execution.getExecutionId(), execution.getWorkflowId());
    }

    @Override
    public void onWorkflowStarted(WorkflowExecution execution) {
        log.info("Workflow started. executionId={}, workflowId={}", execution.getExecutionId(), execution.getWorkflowId());
    }

    @Override
    public void onWorkflowStopping(WorkflowExecution execution) {
        log.info("Workflow stopping. executionId={}, workflowId={}", execution.getExecutionId(), execution.getWorkflowId());
    }

    @Override
    public void onWorkflowStopped(WorkflowExecution execution) {
        log.info("Workflow stopped. executionId={}, workflowId={}", execution.getExecutionId(), execution.getWorkflowId());
    }

    @Override
    public void onWorkflowCompleted(WorkflowExecution execution) {
        log.info("Workflow completed. executionId={}, workflowId={}", execution.getExecutionId(), execution.getWorkflowId());
    }

    @Override
    public void onWorkflowFailed(WorkflowExecution execution) {
        log.info("Workflow failed. executionId={}, workflowId={}, message={}",
                execution.getExecutionId(),
                execution.getWorkflowId(),
                execution.getFailureMessage());
    }

    @Override
    public void onWorkflowCancelled(WorkflowExecution execution) {
        log.info("Workflow cancelled. executionId={}, workflowId={}", execution.getExecutionId(), execution.getWorkflowId());
    }
}
