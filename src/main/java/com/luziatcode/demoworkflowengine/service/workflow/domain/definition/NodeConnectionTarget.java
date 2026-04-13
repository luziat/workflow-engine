package com.luziatcode.demoworkflowengine.service.workflow.domain.definition;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class NodeConnectionTarget {
    private String node;
    private String type;
    private int index;
}
