package com.luziatcode.demoworkflowengine.service.workflow.engine;

import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.domain.execution.WorkflowExecution;

import java.util.Map;

public record NodeExecutionContext(
        WorkflowDefinition definition,
        WorkflowExecution execution,
        Node node,
        Map<String, Object> resolvedParams
) {
}
