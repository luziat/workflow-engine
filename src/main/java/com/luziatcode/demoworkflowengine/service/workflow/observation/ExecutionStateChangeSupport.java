package com.luziatcode.demoworkflowengine.service.workflow.observation;

import com.luziatcode.demoworkflowengine.repository.NodeExecutionRepository;
import com.luziatcode.demoworkflowengine.repository.WorkflowExecutionRepository;
import com.luziatcode.demoworkflowengine.service.workflow.domain.common.ExecutionStatus;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.NodeExecution;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
public class ExecutionStateChangeSupport {
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final ExecutionObservationDispatcher observationDispatcher;

    /**
     * 새 workflow execution을 저장하고 READY observation을 발행한다.
     */
    public WorkflowExecution saveNewWorkflowExecution(WorkflowExecution execution) {
        execution.setUpdatedAt(ZonedDateTime.now());
        WorkflowExecution saved = workflowExecutionRepository.save(execution);
        observationDispatcher.dispatchWorkflow(ExecutionStatus.READY, saved);
        return saved;
    }

    /**
     * 상태 변화 없이 workflow execution의 최신 필드만 저장한다.
     */
    public WorkflowExecution saveWorkflowExecution(WorkflowExecution execution) {
        execution.setUpdatedAt(ZonedDateTime.now());
        return workflowExecutionRepository.save(execution);
    }

    /**
     * workflow execution 상태를 전이시키고 실제 변경 시 observation을 발행한다.
     */
    public WorkflowExecution transitionWorkflowStatus(WorkflowExecution execution, ExecutionStatus status) {
        ExecutionStatus previous = execution.getStatus();
        execution.setStatus(status);
        execution.setUpdatedAt(ZonedDateTime.now());
        WorkflowExecution saved = workflowExecutionRepository.save(execution);
        if (previous != status) {
            observationDispatcher.dispatchWorkflow(status, saved);
        }
        return saved;
    }

    /**
     * node execution 시작 상태를 저장하고 started observation을 발행한다.
     */
    public NodeExecution startNodeExecution(NodeExecution nodeExecution) {
        nodeExecution.setStatus(ExecutionStatus.RUNNING);
        NodeExecution saved = nodeExecutionRepository.save(nodeExecution);
        observationDispatcher.dispatchNode(ExecutionStatus.RUNNING, saved);
        return saved;
    }

    /**
     * node execution 상태를 전이시키고 실제 변경 시 observation을 발행한다.
     */
    public NodeExecution transitionNodeStatus(NodeExecution nodeExecution, ExecutionStatus status) {
        ExecutionStatus previous = nodeExecution.getStatus();
        nodeExecution.setStatus(status);
        NodeExecution saved = nodeExecutionRepository.save(nodeExecution);
        if (previous != status) {
            observationDispatcher.dispatchNode(status, saved);
        }
        return saved;
    }
}
