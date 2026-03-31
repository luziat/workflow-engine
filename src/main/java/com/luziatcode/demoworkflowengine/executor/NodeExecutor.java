package com.luziatcode.demoworkflowengine.executor;

import com.luziatcode.demoworkflowengine.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.engine.NodeResult;

public interface NodeExecutor {
    String getType();

    NodeResult execute(NodeExecutionContext context);
}
