package com.luziatcode.demoworkflowengine.service.workflow.listener;

import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.NodeExecution;
import com.luziatcode.demoworkflowengine.service.workflow.observation.NodeExecutionListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingNodeExecutionListener implements NodeExecutionListener {

    @Override
    public void onNodeStarted(NodeExecution nodeExecution) {
        log.info("Node started. executionId={}, nodeId={}", nodeExecution.getExecutionId(), nodeExecution.getNodeId());
    }

    @Override
    public void onNodeCompleted(NodeExecution nodeExecution) {
        log.info("Node completed. executionId={}, nodeId={}", nodeExecution.getExecutionId(), nodeExecution.getNodeId());
    }

    @Override
    public void onNodeFailed(NodeExecution nodeExecution) {
        log.info("Node failed. executionId={}, nodeId={}, message={}",
                nodeExecution.getExecutionId(),
                nodeExecution.getNodeId(),
                nodeExecution.getMessage());
    }

    @Override
    public void onNodeStopped(NodeExecution nodeExecution) {
        log.info("Node stopped. executionId={}, nodeId={}, message={}",
                nodeExecution.getExecutionId(),
                nodeExecution.getNodeId(),
                nodeExecution.getMessage());
    }
}
