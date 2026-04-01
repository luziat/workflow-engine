package com.luziatcode.demoworkflowengine.service.workflow.action;

import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeResult;

public interface Action {
    ActionType getType();

    NodeResult execute(NodeExecutionContext context);
}
