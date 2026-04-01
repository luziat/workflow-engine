package com.luziatcode.demoworkflowengine.service.workflow.task;

import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeResult;

public interface NodeTask {
    String getType();

    NodeResult execute(NodeExecutionContext context);
}
