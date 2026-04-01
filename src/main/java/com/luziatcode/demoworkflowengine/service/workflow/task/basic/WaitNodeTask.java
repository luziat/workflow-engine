package com.luziatcode.demoworkflowengine.service.workflow.task.basic;

import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeResult;
import com.luziatcode.demoworkflowengine.service.workflow.task.NodeTask;
import org.springframework.stereotype.Component;

@Component
public class WaitNodeTask implements NodeTask {
    @Override
    public String getType() {
        return "wait";
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        return NodeResult.waitForSignal("Waiting for external resume input");
    }
}
