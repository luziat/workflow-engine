package com.luziatcode.demoworkflowengine.service.workflow.domain;

public enum NodeType {
    START,
    SWITCH,
    MERGE,
    LOOP,

    NOTE,
    TIMER,
    GENERIC,
    HTTP
}
