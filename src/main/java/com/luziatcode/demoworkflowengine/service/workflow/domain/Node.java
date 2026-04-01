package com.luziatcode.demoworkflowengine.service.workflow.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Getter
@ToString
public class Node {
    private String nodeId;
    private String name;
    private ActionType actionType;
    private Map<String, Object> params = new LinkedHashMap<>();
}
