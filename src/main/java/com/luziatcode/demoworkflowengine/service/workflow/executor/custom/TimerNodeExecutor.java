package com.luziatcode.demoworkflowengine.service.workflow.executor.custom;

import com.luziatcode.demoworkflowengine.service.workflow.domain.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

@Component
public class TimerNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.TIMER;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        try {
            Thread.sleep((long)(2000));
        } catch (Exception e) {

        }
    }
}
