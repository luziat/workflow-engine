package com.luziatcode.demoworkflowengine.service.workflow.task.custom;

import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeResult;
import com.luziatcode.demoworkflowengine.service.workflow.task.NodeTask;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TaskNodeTask implements NodeTask {
    @Override
    public String getType() {
        return "task";
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        String taskName = context.node().getName();
        if (taskName == null || taskName.isBlank()) {
            taskName = String.valueOf(context.node().getParams().getOrDefault("name", context.node().getNodeId()));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("lastTask", taskName);
        output.put("handledBy", context.node().getNodeId());
        return NodeResult.next(output);
    }
}
