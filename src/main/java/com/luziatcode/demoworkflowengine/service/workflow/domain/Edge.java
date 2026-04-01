package com.luziatcode.demoworkflowengine.service.workflow.domain;

import com.fasterxml.jackson.annotation.JsonAlias;

public class Edge {
    private String edgeId;
    private String from;
    private String to;
    private String condition;

    public String getEdgeId() {
        return edgeId;
    }

    @JsonAlias("id")
    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}
