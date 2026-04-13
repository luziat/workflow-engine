package com.luziatcode.demoworkflowengine.service.workflow.domain.definition;

import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Getter
@ToString
public class Node {
    private String id;
    private String name;
    private NodeType type;
    private boolean disabled;
    private Map<String, Object> params = new LinkedHashMap<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
