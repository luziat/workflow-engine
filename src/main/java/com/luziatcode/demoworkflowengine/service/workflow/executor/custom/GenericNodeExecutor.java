package com.luziatcode.demoworkflowengine.service.workflow.executor.custom;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

@Component
public class GenericNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.GENERIC;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        String taskName = context.node().getName();
        if (taskName == null || taskName.isBlank()) {
            taskName = String.valueOf(context.node().getParams().getOrDefault("name", context.node().getId()));
        }
        context.execution().getContext().put("lastTask", taskName);
        context.execution().getContext().put("handledBy", context.node().getId());
    }
}
