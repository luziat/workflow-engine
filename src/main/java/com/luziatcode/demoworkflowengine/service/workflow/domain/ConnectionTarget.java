package com.luziatcode.demoworkflowengine.service.workflow.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ConnectionTarget {
    private String node;
    private String type;
    private int index;
}
