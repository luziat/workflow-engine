package com.luziatcode.demoworkflowengine.service.workflow.action.base;

import com.luziatcode.demoworkflowengine.service.workflow.action.Action;
import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StartAction implements Action {
    @Override
    public ActionType getType() {
        return ActionType.START;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        return NodeResult.next(Map.of("started", true));
    }
}
