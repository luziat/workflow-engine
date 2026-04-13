package com.luziatcode.demoworkflowengine.service.workflow.executor.custom;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

@Component
public class HttpNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.HTTP;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        String url = String.valueOf(context.node().getParams().getOrDefault("url", "about:blank"));
        context.execution().getContext().put("lastHttpUrl", url);
        context.execution().getContext().put("httpStatus", 200);
        context.execution().getContext().put("httpBody", "demo-response");
    }
}
