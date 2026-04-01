package com.luziatcode.demoworkflowengine.service.workflow.action.custom;

import com.luziatcode.demoworkflowengine.service.workflow.action.Action;
import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class GenericAction implements Action {
    @Override
    public ActionType getType() {
        return ActionType.GENERIC;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        String taskName = context.node().getName();
        if (taskName == null || taskName.isBlank()) {
            taskName = String.valueOf(context.node().getParams().getOrDefault("name", context.node().getNodeId()));
        }
        context.execution().getContext().put("lastTask", taskName);
        context.execution().getContext().put("handledBy", context.node().getNodeId());
    }
}
