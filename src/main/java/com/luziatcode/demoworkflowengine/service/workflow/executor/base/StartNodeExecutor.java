package com.luziatcode.demoworkflowengine.service.workflow.executor.base;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

@Component
public class StartNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.START;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        context.execution().getContext().put("started", true);
    }
}
