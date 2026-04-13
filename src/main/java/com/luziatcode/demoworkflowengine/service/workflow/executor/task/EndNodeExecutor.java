package com.luziatcode.demoworkflowengine.service.workflow.executor.task;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

/**
 * 테스트용 종료 노드로 마지막 실행 태스크 이름과 처리 노드 id 를 context 에 기록한다.
 *
 * <p>params 예시:
 * <pre>{@code
 * {
 *   "name": "Runtime End Task"
 * }
 * }</pre>
 */
@Component
public class EndNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.END;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        String taskName = context.node().getName();
        if (taskName == null || taskName.isBlank()) {
            taskName = String.valueOf(context.resolvedParams().getOrDefault("name", context.node().getId()));
        }
        context.execution().getContext().put("lastTask", taskName);
        context.execution().getContext().put("handledBy", context.node().getId());
    }
}
