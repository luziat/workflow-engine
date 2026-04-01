package com.luziatcode.demoworkflowengine.service.workflow.task.custom;

import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeResult;
import com.luziatcode.demoworkflowengine.service.workflow.task.NodeTask;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HttpNodeTask implements NodeTask {
    @Override
    public String getType() {
        return "http";
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        String url = String.valueOf(context.node().getParams().getOrDefault("url", "about:blank"));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("lastHttpUrl", url);
        output.put("httpStatus", 200);
        output.put("httpBody", "demo-response");

        return NodeResult.next(output);
    }
}
