package com.luziatcode.demoworkflowengine.config;

import com.luziatcode.demoworkflowengine.service.WorkflowDefinitionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapConfig {

    @Bean
    CommandLineRunner bootstrapSampleWorkflow(WorkflowDefinitionService workflowDefinitionService) {
        return args -> {
            try {
                workflowDefinitionService.getLatest("sample-workflow");
            } catch (Exception ignored) {
                workflowDefinitionService.createSample();
            }
        };
    }
}
