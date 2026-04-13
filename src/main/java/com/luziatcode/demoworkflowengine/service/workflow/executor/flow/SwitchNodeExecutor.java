package com.luziatcode.demoworkflowengine.service.workflow.executor.flow;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 조건 목록을 평가하여 true/false output branch 를 선택하는 executor.
 *
 * <p>지원 연산:
 * <ul>
 *   <li>{@code equals}</li>
 *   <li>{@code notEquals}</li>
 *   <li>{@code greaterThan}</li>
 *   <li>{@code greaterThanOrEqual}</li>
 *   <li>{@code lessThan}</li>
 *   <li>{@code lessThanOrEqual}</li>
 * </ul>
 *
 * <p>문자열 비교 params 예시:
 * <pre>{@code
 * {
 *   "conditions": {
 *     "conditions": [
 *       {
 *         "leftValue": "approved",
 *         "rightValue": "approved",
 *         "operator": {
 *           "operation": "equals"
 *         }
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>숫자 비교 params 예시:
 * <pre>{@code
 * {
 *   "conditions": {
 *     "conditions": [
 *       {
 *         "leftValue": "10",
 *         "rightValue": 5,
 *         "operator": {
 *           "operation": "greaterThan"
 *         }
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 */
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
        Object conditionsObject = context.resolvedParams().get("conditions");
        if (!(conditionsObject instanceof Map<?, ?> conditionsMap)) {
            return List.of(1);
        }

        /* IF 노드의 조건 목록을 읽어 true/false 출력 포트를 결정한다. */
        Object entriesObject = conditionsMap.get("conditions");
        if (!(entriesObject instanceof List<?> entries) || entries.isEmpty()) {
            return List.of(1);
        }

        for (Object entryObject : entries) {
            if (!(entryObject instanceof Map<?, ?> entry)) {
                return List.of(1);
            }

            Object leftValue = entry.get("leftValue");
            Object rightValue = entry.get("rightValue");
            if (leftValue == null || String.valueOf(leftValue).isBlank()) {
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

    private boolean matches(Object leftValue, Object rightValue, String operation) {
        return switch (operation) {
            case "equals" -> String.valueOf(leftValue).equals(String.valueOf(rightValue));
            case "notEquals" -> !String.valueOf(leftValue).equals(String.valueOf(rightValue));
            case "greaterThan" -> matchesNumeric(leftValue, rightValue, comparison -> comparison > 0);
            case "greaterThanOrEqual" -> matchesNumeric(leftValue, rightValue, comparison -> comparison >= 0);
            case "lessThan" -> matchesNumeric(leftValue, rightValue, comparison -> comparison < 0);
            case "lessThanOrEqual" -> matchesNumeric(leftValue, rightValue, comparison -> comparison <= 0);
            default -> false;
        };
    }

    private boolean matchesNumeric(Object leftValue, Object rightValue, java.util.function.IntPredicate predicate) {
        BigDecimal leftNumber = toBigDecimal(leftValue);
        BigDecimal rightNumber = toBigDecimal(rightValue);
        if (leftNumber == null || rightNumber == null) {
            return false;
        }
        return predicate.test(leftNumber.compareTo(rightNumber));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }

        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }

        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
