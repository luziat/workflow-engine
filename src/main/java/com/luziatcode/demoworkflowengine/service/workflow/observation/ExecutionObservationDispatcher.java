package com.luziatcode.demoworkflowengine.service.workflow.observation;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.NodeExecution;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionObservationDispatcher {
    private final List<WorkflowExecutionListener> workflowExecutionListeners;
    private final List<NodeExecutionListener> nodeExecutionListeners;
    @Qualifier("observationTaskExecutor")
    private final TaskExecutor observationTaskExecutor;

    public void dispatchWorkflow(ExecutionStatus status, WorkflowExecution execution) {
        for (WorkflowExecutionListener listener : workflowExecutionListeners) {
            observationTaskExecutor.execute(() -> invokeWorkflowListener(listener, status, execution));
        }
    }

    public void dispatchNode(ExecutionStatus status, NodeExecution nodeExecution) {
        for (NodeExecutionListener listener : nodeExecutionListeners) {
            observationTaskExecutor.execute(() -> invokeNodeListener(listener, status, nodeExecution));
        }
    }

    private void invokeWorkflowListener(WorkflowExecutionListener listener,
                                        ExecutionStatus status,
                                        WorkflowExecution execution) {
        try {
            switch (status) {
                case READY -> listener.onWorkflowReady(execution);
                case RUNNING -> listener.onWorkflowStarted(execution);
                case STOPPING -> listener.onWorkflowStopping(execution);
                case STOPPED -> listener.onWorkflowStopped(execution);
                case SUCCESS -> listener.onWorkflowCompleted(execution);
                case FAILED -> listener.onWorkflowFailed(execution);
                case CANCELLED -> listener.onWorkflowCancelled(execution);
            }
        } catch (Exception exception) {
            log.warn("Workflow execution listener failed. executionId={}, status={}", execution.getExecutionId(), status, exception);
        }
    }

    private void invokeNodeListener(NodeExecutionListener listener,
                                    ExecutionStatus status,
                                    NodeExecution nodeExecution) {
        try {
            switch (status) {
                case RUNNING -> listener.onNodeStarted(nodeExecution);
                case SUCCESS -> listener.onNodeCompleted(nodeExecution);
                case FAILED -> listener.onNodeFailed(nodeExecution);
                case STOPPED -> listener.onNodeStopped(nodeExecution);
                default -> {
                }
            }
        } catch (Exception exception) {
            log.warn("Node execution listener failed. executionId={}, nodeId={}, status={}",
                    nodeExecution.getExecutionId(),
                    nodeExecution.getNodeId(),
                    status,
                    exception);
        }
    }
}
