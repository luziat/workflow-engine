package com.luziatcode.demoworkflowengine.engine;

import com.luziatcode.demoworkflowengine.domain.Node;
import com.luziatcode.demoworkflowengine.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.domain.WorkflowExecution;

public record NodeExecutionContext(
        WorkflowDefinition definition,
        WorkflowExecution execution,
        Node node
) {
}
