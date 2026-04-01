package com.luziatcode.demoworkflowengine.service.workflow.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class WorkflowDefinition {
    private String id;
    private int version;
    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes != null ? new ArrayList<>(nodes) : new ArrayList<>();
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges != null ? new ArrayList<>(edges) : new ArrayList<>();
    }
}
