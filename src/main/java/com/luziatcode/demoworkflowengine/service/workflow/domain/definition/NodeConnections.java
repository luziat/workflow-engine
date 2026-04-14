package com.luziatcode.demoworkflowengine.service.workflow.domain.definition;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class NodeConnections {
    private List<List<NodeConnectionTarget>> main = new ArrayList<>();
}
