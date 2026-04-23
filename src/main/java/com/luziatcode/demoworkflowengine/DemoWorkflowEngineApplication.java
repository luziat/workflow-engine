package com.luziatcode.demoworkflowengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DemoWorkflowEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoWorkflowEngineApplication.class, args);
    }
}
