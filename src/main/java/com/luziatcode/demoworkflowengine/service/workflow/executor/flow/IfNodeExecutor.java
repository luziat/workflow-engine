package com.luziatcode.demoworkflowengine.service.workflow.executor.flow;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import org.springframework.stereotype.Component;

/**
 * IF 조건을 평가하여 true/false output branch 를 선택하는 executor.
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
 * <p>params 예시:
 * <pre>{@code
 * {
 *   "conditions": {
 *     "conditions": [
 *       {
 *         "leftValue": "<<order.totalAmount>>",
 *         "rightValue": 10000,
 *         "operator": {
 *           "operation": "greaterThanOrEqual"
 *         }
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 */
@Component
public class IfNodeExecutor extends ConditionalBranchNodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.IF;
    }
}
