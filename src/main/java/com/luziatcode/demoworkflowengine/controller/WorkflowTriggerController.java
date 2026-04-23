package com.luziatcode.demoworkflowengine.controller;

import com.luziatcode.demoworkflowengine.dto.WorkflowExecutionResponse;
import com.luziatcode.demoworkflowengine.service.workflow.WorkflowDefinitionService;
import com.luziatcode.demoworkflowengine.service.workflow.WorkflowExecutionService;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.trigger.StartTrigger;
import com.luziatcode.demoworkflowengine.service.workflow.trigger.StartTriggerResolver;
import com.luziatcode.demoworkflowengine.service.workflow.trigger.StartTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/triggers/webhooks")
@RequiredArgsConstructor
public class WorkflowTriggerController {
    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowExecutionService workflowExecutionService;
    private final StartTriggerResolver startTriggerResolver;

    @PostMapping("/{workflowId}")
    public WorkflowExecutionResponse triggerWebhook(@PathVariable String workflowId,
                                                    @RequestParam String token,
                                                    @RequestBody(required = false) Map<String, Object> input) {
        WorkflowDefinition definition = workflowDefinitionService.getLatest(workflowId);
        StartTrigger trigger = startTriggerResolver.resolve(definition);
        if (trigger.type() != StartTriggerType.WEBHOOK) {
            throw new IllegalArgumentException("Workflow is not configured for webhook trigger: " + workflowId);
        }
        if (!trigger.enabled()) {
            throw new IllegalArgumentException("Webhook trigger is disabled for workflow: " + workflowId);
        }
        if (!trigger.webhookToken().equals(token)) {
            throw new IllegalArgumentException("Invalid webhook token for workflow: " + workflowId);
        }

        Map<String, Object> mergedInput = new LinkedHashMap<>();
        if (input != null) {
            mergedInput.putAll(input);
        }
        mergedInput = startTriggerResolver.buildTriggerContext(definition, StartTriggerType.WEBHOOK, mergedInput);
        return WorkflowExecutionResponse.from(workflowExecutionService.start(definition, mergedInput));
    }
}
