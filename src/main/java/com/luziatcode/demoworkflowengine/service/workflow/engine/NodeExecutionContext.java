package com.luziatcode.demoworkflowengine.service.workflow.engine;

import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;

public record NodeExecutionContext(
        WorkflowDefinition definition,
        WorkflowExecution execution,
        Node node
) {
}
