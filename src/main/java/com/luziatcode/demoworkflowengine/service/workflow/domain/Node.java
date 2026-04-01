package com.luziatcode.demoworkflowengine.service.workflow.domain;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.LinkedHashMap;
import java.util.Map;

public class Node {
    private String nodeId;
    private String name;
    private String type;
    private Map<String, Object> params = new LinkedHashMap<>();

    public String getNodeId() {
        return nodeId;
    }

    @JsonAlias("id")
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getName() {
        return name;
    }

    @JsonAlias("nodeName")
    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
    }
}
