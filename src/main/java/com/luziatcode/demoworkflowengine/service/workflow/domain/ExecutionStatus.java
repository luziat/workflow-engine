package com.luziatcode.demoworkflowengine.service.workflow.domain;

public enum ExecutionStatus {
    READY,
    RUNNING,
    STOPPING,
    STOPPED,
    SUCCESS,
    FAILED,
    CANCELLED
}
