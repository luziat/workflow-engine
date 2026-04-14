package com.luziatcode.demoworkflowengine.controller;

import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.WorkflowDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowDefinitionController {
    private final WorkflowDefinitionService workflowDefinitionService;

    @PostMapping
    public WorkflowDefinition save(@RequestBody WorkflowDefinition definition) {
        return workflowDefinitionService.save(definition);
    }

    @GetMapping("/{workflowId}")
    public WorkflowDefinition getLatest(@PathVariable String workflowId) {
        return workflowDefinitionService.getLatest(workflowId);
    }
}
