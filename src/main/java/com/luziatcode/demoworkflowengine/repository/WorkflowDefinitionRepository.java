package com.luziatcode.demoworkflowengine.repository;

import com.luziatcode.demoworkflowengine.domain.WorkflowDefinition;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class WorkflowDefinitionRepository {
    private final Map<String, WorkflowDefinition> store = new ConcurrentHashMap<>();

    public WorkflowDefinition save(WorkflowDefinition definition) {
        store.put(key(definition.getId(), definition.getVersion()), definition);
        return definition;
    }

    public Optional<WorkflowDefinition> findByIdAndVersion(String id, int version) {
        return Optional.ofNullable(store.get(key(id, version)));
    }

    public Optional<WorkflowDefinition> findLatestById(String id) {
        return store.values().stream()
                .filter(def -> def.getId().equals(id))
                .max((left, right) -> Integer.compare(left.getVersion(), right.getVersion()));
    }

    private String key(String id, int version) {
        return id + ":" + version;
    }
}
