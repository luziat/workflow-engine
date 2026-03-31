package com.luziatcode.demoworkflowengine.executor;

import com.luziatcode.demoworkflowengine.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.engine.NodeResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TaskNodeExecutor implements NodeExecutor {
    @Override
    public String getType() {
        return "task";
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        String taskName = String.valueOf(context.node().getParams().getOrDefault("name", context.node().getId()));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("lastTask", taskName);
        output.put("handledBy", context.node().getId());
        return NodeResult.next(output);
    }
}
