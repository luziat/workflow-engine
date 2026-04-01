package com.luziatcode.demoworkflowengine.service.workflow.action;

import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;

public interface Action {
    ActionType getType();

    void execute(NodeExecutionContext context);
}
