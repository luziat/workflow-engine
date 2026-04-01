package com.luziatcode.demoworkflowengine.service.workflow.engine;

import com.luziatcode.demoworkflowengine.service.workflow.domain.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.WorkflowExecution;

public record NodeExecutionContext(
        WorkflowDefinition definition,
        WorkflowExecution execution,
        Node node
) {
}
