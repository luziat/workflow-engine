package com.luziatcode.demoworkflowengine.service.workflow.executor.base;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoopNodeExecutor implements NodeExecutor {
    @Override
    public NodeType getType() {
        return NodeType.LOOP;
    }

    @Override
    public void execute(NodeExecutionContext context) {
    }

    @Override
    public List<Integer> selectOutputs(NodeExecutionContext context) {
        Object items = context.execution().getContext().get("items");
        if (items instanceof Iterable<?>) {
            return List.of(0);
        }
        return List.of(1);
    }
}
