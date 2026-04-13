package com.luziatcode.demoworkflowengine.service.workflow.executor.task;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 주어진 시간만큼 대기한 뒤 다음 노드로 진행하는 executor.
 *
 * <p>params 예시:
 * <pre>{@code
 * {
 *   "waitMillis": 3000
 * }
 * }</pre>
 */
@Slf4j
@Component
public class TimerNodeExecutor implements NodeExecutor {
    private static final long DEFAULT_WAIT_MILLIS = 2_000L;

    @Override
    public NodeType getType() {
        return NodeType.TIMER;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        try {
            Thread.sleep(resolveWaitMillis(context.node().getParams()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.debug("Timer node interrupted. executionId={}, nodeId={}",
                    context.execution().getExecutionId(),
                    context.node().getId());
        }
    }

    private long resolveWaitMillis(Map<String, Object> params) {
        Object waitMillis = params.get("waitMillis");
        if (waitMillis instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        return DEFAULT_WAIT_MILLIS;
    }
}
