package com.luziatcode.demoworkflowengine.service.workflow.domain.definition;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
public class WorkflowDefinition {
    private String id;
    private String name;
    private int version;
    private List<Node> nodes = new ArrayList<>();
    private Map<String, NodeOutputs> connections = new LinkedHashMap<>();
}
