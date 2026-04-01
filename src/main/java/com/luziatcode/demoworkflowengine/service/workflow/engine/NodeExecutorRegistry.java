package com.luziatcode.demoworkflowengine.service.workflow.engine;

import com.luziatcode.demoworkflowengine.service.workflow.action.Action;
import com.luziatcode.demoworkflowengine.service.workflow.domain.ActionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NodeExecutorRegistry {
    private final List<Action> actions;
    private Map<ActionType, Action> executors;

    public Action getRequired(ActionType type) {
        if (executors == null) {
            executors = actions.stream().collect(Collectors.toMap(Action::getType, Function.identity()));
        }
        Action executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("Unsupported node type: " + type);
        }
        return executor;
    }
}
