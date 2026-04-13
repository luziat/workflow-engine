package com.luziatcode.demoworkflowengine.service.workflow.executor;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;

import java.util.List;

public interface NodeExecutor {
    /* node type */
    NodeType getType();

    /* 노드 실행 */
    void execute(NodeExecutionContext context);

    /* 출력 포트(output index) 결정 */
    default List<Integer> selectOutputs(NodeExecutionContext context) {
        return List.of(0);
    }

    /* 노드 실행 중지 */
    default void stop(String reason) {
    }
}
