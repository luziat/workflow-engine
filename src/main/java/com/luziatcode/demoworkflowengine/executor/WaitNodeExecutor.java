package com.luziatcode.demoworkflowengine.executor;

import com.luziatcode.demoworkflowengine.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.engine.NodeResult;
import org.springframework.stereotype.Component;

@Component
public class WaitNodeExecutor implements NodeExecutor {
    @Override
    public String getType() {
        return "wait";
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        return NodeResult.waitForSignal("Waiting for external resume input");
    }
}
