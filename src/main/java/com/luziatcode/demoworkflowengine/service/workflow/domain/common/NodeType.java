package com.luziatcode.demoworkflowengine.service.workflow.domain.common;

public enum NodeType {
    /* 기본 노드 */
    START,
    SWITCH,
    MERGE,
    LOOP,
    NOTE,

    /* 커스텀 노드 */
    GENERIC,
    TIMER,
    HTTP
}
