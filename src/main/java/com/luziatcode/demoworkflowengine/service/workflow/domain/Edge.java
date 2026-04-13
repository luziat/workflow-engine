package com.luziatcode.demoworkflowengine.service.workflow.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Edge {
    @JsonAlias("id")
    private String edgeId;
    private String from;
    private String to;
}
