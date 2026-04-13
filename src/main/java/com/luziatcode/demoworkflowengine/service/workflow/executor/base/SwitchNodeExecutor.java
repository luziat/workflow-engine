package com.luziatcode.demoworkflowengine.service.workflow.executor.base;

import com.luziatcode.demoworkflowengine.service.workflow.domain.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SwitchNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.SWITCH;
    }

    @Override
    public void execute(NodeExecutionContext context) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Integer> selectOutputs(NodeExecutionContext context) {
        Object conditionsObject = context.node().getParams().get("conditions");
        if (!(conditionsObject instanceof Map<?, ?> conditionsMap)) {
            return List.of(1);
        }

        // n8n IF 노드의 조건 목록을 읽어 true/false 출력 포트를 결정한다.
        Object entriesObject = conditionsMap.get("conditions");
        if (!(entriesObject instanceof List<?> entries) || entries.isEmpty()) {
            return List.of(1);
        }

        for (Object entryObject : entries) {
            if (!(entryObject instanceof Map<?, ?> entry)) {
                return List.of(1);
            }

            String leftValue = String.valueOf(entry.get("leftValue") != null ? entry.get("leftValue") : "");
            String rightValue = String.valueOf(entry.get("rightValue") != null ? entry.get("rightValue") : "");
            if (leftValue.isBlank()) {
                return List.of(1);
            }

            Object operatorObject = entry.get("operator");
            String operation = "equals";
            if (operatorObject instanceof Map<?, ?> operatorMap && operatorMap.get("operation") != null) {
                operation = String.valueOf(operatorMap.get("operation"));
            }

            if (!matches(leftValue, rightValue, operation)) {
                return List.of(1);
            }
        }

        return List.of(0);
    }

    private boolean matches(String leftValue, String rightValue, String operation) {
        return switch (operation) {
            case "equals" -> leftValue.equals(rightValue);
            case "notEquals" -> !leftValue.equals(rightValue);
            default -> false;
        };
    }
}
