package com.luziatcode.demoworkflowengine.service.workflow.executor.flow;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

/**
 * workflow 시작 시 실행 context 에 started 플래그를 기록하는 executor.
 *
 * <p>params 예시:
 * <pre>{@code
 * {}
 * }</pre>
 */
@Component
public class StartNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.START;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        context.execution().getContext().put("started", true);
    }
}
