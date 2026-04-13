package com.luziatcode.demoworkflowengine.service.workflow.executor.flow;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

/**
 * 여러 입력 포트가 모두 도착한 뒤 다음 흐름으로 진행하는 merge executor.
 *
 * <p>params 예시:
 * <pre>{@code
 * {}
 * }</pre>
 */
@Component
public class MergeNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.MERGE;
    }

    @Override
    public void execute(NodeExecutionContext context) {
    }
}
