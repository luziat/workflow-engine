package com.luziatcode.demoworkflowengine.executor;

import com.luziatcode.demoworkflowengine.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.engine.NodeResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SwitchNodeExecutor implements NodeExecutor {
    @Override
    public String getType() {
        return "switch";
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        return NodeResult.next(Map.of());
    }
}
