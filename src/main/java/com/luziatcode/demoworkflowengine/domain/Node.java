package com.luziatcode.demoworkflowengine.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class Node {
    private String id;
    private String type;
    private Map<String, Object> params = new LinkedHashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
