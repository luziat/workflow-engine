package com.luziatcode.demoworkflowengine.service.workflow.action.custom;

import com.luziatcode.demoworkflowengine.service.workflow.action.Action;
import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GenericAction implements Action {
    @Override
    public ActionType getType() {
        return ActionType.GENERIC;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        String taskName = context.node().getName();
        if (taskName == null || taskName.isBlank()) {
            taskName = String.valueOf(context.node().getParams().getOrDefault("name", context.node().getNodeId()));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("lastTask", taskName);
        output.put("handledBy", context.node().getNodeId());
        return NodeResult.next(output);
    }
}
