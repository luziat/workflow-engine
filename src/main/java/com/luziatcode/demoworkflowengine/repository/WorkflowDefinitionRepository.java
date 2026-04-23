package com.luziatcode.demoworkflowengine.repository;

import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
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

    public List<WorkflowDefinition> findAllById(String id) {
        return store.values().stream()
                .filter(def -> def.getId().equals(id))
                .sorted(Comparator.comparingInt(WorkflowDefinition::getVersion).reversed())
                .toList();
    }

    public List<WorkflowDefinition> findAllLatest() {
        return store.values().stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkflowDefinition::getId,
                        definition -> definition,
                        (left, right) -> left.getVersion() >= right.getVersion() ? left : right
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(WorkflowDefinition::getId))
                .toList();
    }

    public boolean deleteByIdAndVersion(String id, int version) {
        return store.remove(key(id, version)) != null;
    }

    public boolean deleteAllById(String id) {
        List<String> keysToDelete = store.keySet().stream()
                .filter(key -> key.startsWith(id + ":"))
                .toList();
        keysToDelete.forEach(store::remove);
        return !keysToDelete.isEmpty();
    }

    private String key(String id, int version) {
        return id + ":" + version;
    }
}
