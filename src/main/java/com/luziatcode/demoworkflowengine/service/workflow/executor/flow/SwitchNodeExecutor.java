package com.luziatcode.demoworkflowengine.service.workflow.executor.flow;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import org.springframework.stereotype.Component;

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
public class SwitchNodeExecutor extends ConditionalBranchNodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.SWITCH;
    }
}
