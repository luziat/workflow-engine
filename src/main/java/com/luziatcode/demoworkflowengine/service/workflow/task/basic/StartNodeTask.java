package com.luziatcode.demoworkflowengine.service.workflow.task.basic;

import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeResult;
import com.luziatcode.demoworkflowengine.service.workflow.task.NodeTask;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StartNodeTask implements NodeTask {
    @Override
    public String getType() {
        return "start";
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        return NodeResult.next(Map.of("started", true));
    }
}
