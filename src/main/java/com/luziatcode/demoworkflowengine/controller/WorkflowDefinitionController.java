package com.luziatcode.demoworkflowengine.controller;

import com.luziatcode.demoworkflowengine.domain.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.WorkflowDefinitionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowDefinitionController {
    private final WorkflowDefinitionService workflowDefinitionService;

    public WorkflowDefinitionController(WorkflowDefinitionService workflowDefinitionService) {
        this.workflowDefinitionService = workflowDefinitionService;
    }

    @PostMapping
    public WorkflowDefinition save(@RequestBody WorkflowDefinition definition) {
        return workflowDefinitionService.save(definition);
    }

    @GetMapping("/{workflowId}")
    public WorkflowDefinition getLatest(@PathVariable String workflowId) {
        return workflowDefinitionService.getLatest(workflowId);
    }

    @PostMapping("/sample")
    public WorkflowDefinition createSample() {
        return workflowDefinitionService.createSample();
    }
}
