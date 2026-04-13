package com.luziatcode.demoworkflowengine.service.workflow.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class NodeConnections {
    private List<List<ConnectionTarget>> main = new ArrayList<>();

    public void setMain(List<List<ConnectionTarget>> main) {
        this.main = main != null ? main : new ArrayList<>();
    }
}
