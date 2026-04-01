package com.luziatcode.demoworkflowengine.service.workflow.action.base;

import com.luziatcode.demoworkflowengine.service.workflow.action.Action;
import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class StartAction implements Action {
    @Override
    public ActionType getType() {
        return ActionType.START;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        context.execution().getContext().put("started", true);
    }
}
