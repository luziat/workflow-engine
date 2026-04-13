package com.luziatcode.demoworkflowengine.service.workflow.executor.base;

import com.luziatcode.demoworkflowengine.service.workflow.domain.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

@Component
public class MergeNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.MERGE;
    }

    @Override
    public void execute(NodeExecutionContext context) {
    }
}
