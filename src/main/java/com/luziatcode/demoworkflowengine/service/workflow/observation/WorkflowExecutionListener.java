package com.luziatcode.demoworkflowengine.service.workflow.observation;

import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;

public interface WorkflowExecutionListener {
    default void onWorkflowReady(WorkflowExecution execution) {
    }

    default void onWorkflowStarted(WorkflowExecution execution) {
    }

    default void onWorkflowStopping(WorkflowExecution execution) {
    }

    default void onWorkflowStopped(WorkflowExecution execution) {
    }

    default void onWorkflowCompleted(WorkflowExecution execution) {
    }

    default void onWorkflowFailed(WorkflowExecution execution) {
    }

    default void onWorkflowCancelled(WorkflowExecution execution) {
    }
}
