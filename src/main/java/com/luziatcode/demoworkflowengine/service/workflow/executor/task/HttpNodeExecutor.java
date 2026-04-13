package com.luziatcode.demoworkflowengine.service.workflow.executor.task;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

/**
 * 지정한 URL 을 호출한 것처럼 demo 응답 값을 context 에 기록하는 executor.
 *
 * <p>params 예시:
 * <pre>{@code
 * {
 *   "url": "https://api.example.com/orders/1"
 * }
 * }</pre>
 */
@Component
public class HttpNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.HTTP;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        String url = String.valueOf(context.node().getParams().getOrDefault("url", "about:blank"));
        context.execution().getContext().put("lastHttpUrl", url);
        context.execution().getContext().put("httpStatus", 200);
        context.execution().getContext().put("httpBody", "demo-response");
    }
}
