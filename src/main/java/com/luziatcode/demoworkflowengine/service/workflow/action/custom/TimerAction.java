package com.luziatcode.demoworkflowengine.service.workflow.action.custom;

import com.luziatcode.demoworkflowengine.service.workflow.action.Action;
import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class TimerAction implements Action {
    @Override
    public ActionType getType() {
        return ActionType.TIMER;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        try {
            Thread.sleep((long)(2000));
        } catch (Exception e) {

        }
    }
}
