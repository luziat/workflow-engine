package com.luziatcode.demoworkflowengine.controller;

import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.WorkflowDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowDefinitionController {
    private final WorkflowDefinitionService workflowDefinitionService;

    @PostMapping
    public WorkflowDefinition save(@RequestBody WorkflowDefinition definition) {
        return workflowDefinitionService.save(definition);
    }

    @GetMapping
    public List<WorkflowDefinition> getAllLatest() {
        return workflowDefinitionService.getAllLatest();
    }

    @GetMapping("/{workflowId}")
    public WorkflowDefinition getLatest(@PathVariable String workflowId) {
        return workflowDefinitionService.getLatest(workflowId);
    }

    @GetMapping("/{workflowId}/versions")
    public List<WorkflowDefinition> getAllVersions(@PathVariable String workflowId) {
        return workflowDefinitionService.getAllVersions(workflowId);
    }

    @GetMapping("/{workflowId}/versions/{version}")
    public WorkflowDefinition getRequired(@PathVariable String workflowId,
                                          @PathVariable int version) {
        return workflowDefinitionService.getRequired(workflowId, version);
    }

    @DeleteMapping("/{workflowId}")
    public void delete(@PathVariable String workflowId) {
        workflowDefinitionService.delete(workflowId);
    }

    @DeleteMapping("/{workflowId}/versions/{version}")
    public void delete(@PathVariable String workflowId,
                       @PathVariable int version) {
        workflowDefinitionService.delete(workflowId, version);
    }
}
