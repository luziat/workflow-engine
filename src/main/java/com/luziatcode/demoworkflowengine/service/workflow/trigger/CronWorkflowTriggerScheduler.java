package com.luziatcode.demoworkflowengine.service.workflow.trigger;

import com.luziatcode.demoworkflowengine.service.workflow.WorkflowDefinitionService;
import com.luziatcode.demoworkflowengine.service.workflow.WorkflowExecutionService;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class CronWorkflowTriggerScheduler {
    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowExecutionService workflowExecutionService;
    private final StartTriggerResolver startTriggerResolver;

    private final ConcurrentMap<String, ZonedDateTime> lastCheckedAt = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${workflow.trigger.cron-poll-ms:1000}")
    public void pollCronTriggers() {
        ZonedDateTime now = ZonedDateTime.now();
        for (WorkflowDefinition definition : workflowDefinitionService.getAllLatest()) {
            StartTrigger trigger = startTriggerResolver.resolve(definition);
            if (trigger.type() != StartTriggerType.CRON || !trigger.enabled()) {
                continue;
            }
            if (!isDue(definition, trigger, now)) {
                continue;
            }

            Map<String, Object> input = startTriggerResolver.buildTriggerContext(
                    definition,
                    StartTriggerType.CRON,
                    trigger.inputOrEmpty()
            );
            workflowExecutionService.start(definition, input);
        }
    }

    private boolean isDue(WorkflowDefinition definition, StartTrigger trigger, ZonedDateTime now) {
        ZonedDateTime previousCheck = lastCheckedAt.put(definition.getId(), now);
        ZonedDateTime base = previousCheck != null ? previousCheck.minusNanos(1) : now.minusSeconds(1);
        ZonedDateTime next = CronExpression.parse(trigger.cron()).next(base);
        return next != null && !next.isAfter(now);
    }
}
