package com.luziatcode.demoworkflowengine.service.workflow.observation;

import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.NodeExecution;

public interface NodeExecutionListener {
    default void onNodeStarted(NodeExecution nodeExecution) {
    }

    default void onNodeCompleted(NodeExecution nodeExecution) {
    }

    default void onNodeFailed(NodeExecution nodeExecution) {
    }

    default void onNodeStopped(NodeExecution nodeExecution) {
    }
}
