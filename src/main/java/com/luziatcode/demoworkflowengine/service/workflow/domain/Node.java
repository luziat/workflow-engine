package com.luziatcode.demoworkflowengine.service.workflow.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Getter
@ToString
public class Node {
    @JsonAlias("id")
    private String nodeId;
    private String name;
    private NodeType type;
    private boolean disabled;
    private Map<String, Object> params = new LinkedHashMap<>();

    public void setParams(Map<String, Object> params) {
        this.params = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
    }
}
