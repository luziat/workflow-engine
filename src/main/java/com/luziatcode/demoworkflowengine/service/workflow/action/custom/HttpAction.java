package com.luziatcode.demoworkflowengine.service.workflow.action.custom;

import com.luziatcode.demoworkflowengine.service.workflow.action.Action;
import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class HttpAction implements Action {
    @Override
    public ActionType getType() {
        return ActionType.HTTP;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        String url = String.valueOf(context.node().getParams().getOrDefault("url", "about:blank"));
        context.execution().getContext().put("lastHttpUrl", url);
        context.execution().getContext().put("httpStatus", 200);
        context.execution().getContext().put("httpBody", "demo-response");
    }
}
