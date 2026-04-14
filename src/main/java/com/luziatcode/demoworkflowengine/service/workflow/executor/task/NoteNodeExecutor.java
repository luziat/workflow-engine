package com.luziatcode.demoworkflowengine.service.workflow.executor.task;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

/**
 * 실행 흐름에는 영향을 주지 않고 메모성 노드를 표현하는 executor.
 *
 * <p>params 예시:
 * <pre>{@code
 * {
 *   "text": "이 구간은 운영 승인 후 배포"
 * }
 * }</pre>
 */
@Component
public class NoteNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.NOTE;
    }

    @Override
    public void execute(NodeExecutionContext context) {
    }
}
