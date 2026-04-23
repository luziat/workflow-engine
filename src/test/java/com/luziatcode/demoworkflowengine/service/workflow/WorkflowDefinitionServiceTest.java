package com.luziatcode.demoworkflowengine.service.workflow;

import com.luziatcode.demoworkflowengine.repository.WorkflowDefinitionRepository;
import com.luziatcode.demoworkflowengine.service.workflow.domain.definition.WorkflowDefinition;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutorRegistry;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.EndNodeExecutor;
import com.luziatcode.demoworkflowengine.service.workflow.executor.flow.StartNodeExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDefinitionServiceTest {

    @Test
    @DisplayName("동일 workflowId는 최신 버전보다 큰 version만 저장할 수 있다")
    void saveRequiresVersionGreaterThanLatest() {
        WorkflowDefinitionService service = workflowDefinitionService();

        WorkflowDefinition v2 = definition("approval-flow", 2, "Done V2");
        WorkflowDefinition saved = service.save(v2);
        assertEquals(2, saved.getVersion());

        IllegalArgumentException sameVersion = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition("approval-flow", 2, "Duplicate V2"))
        );
        assertEquals(
                "Workflow version must be greater than latest version 2 for workflow approval-flow",
                sameVersion.getMessage()
        );

        IllegalArgumentException lowerVersion = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition("approval-flow", 1, "Legacy V1"))
        );
        assertEquals(
                "Workflow version must be greater than latest version 2 for workflow approval-flow",
                lowerVersion.getMessage()
        );

        WorkflowDefinition v3 = service.save(definition("approval-flow", 3, "Done V3"));
        assertEquals(3, v3.getVersion());
    }

    @Test
    @DisplayName("새 workflowId는 version 1로 저장할 수 있다")
    void saveAllowsInitialVersionOneForNewWorkflow() {
        WorkflowDefinitionService service = workflowDefinitionService();

        WorkflowDefinition saved = service.save(definition("new-flow", 1, "Initial"));

        assertEquals(1, saved.getVersion());
        assertEquals("new-flow", saved.getId());
    }

    @Test
    @DisplayName("workflowId 전체 삭제 후에는 version 1부터 다시 저장할 수 있다")
    void deleteAllAllowsSavingFromVersionOneAgain() {
        WorkflowDefinitionService service = workflowDefinitionService();
        service.save(definition("reset-flow", 1, "Initial"));
        service.save(definition("reset-flow", 2, "Second"));

        service.delete("reset-flow");

        WorkflowDefinition saved = service.save(definition("reset-flow", 1, "Initial Again"));
        assertEquals(1, saved.getVersion());
    }

    @Test
    @DisplayName("CRON 시작 노드는 cron metadata가 필요하다")
    void saveRequiresCronMetadataForCronTrigger() {
        WorkflowDefinitionService service = workflowDefinitionService();
        WorkflowDefinition definition = definition("cron-flow", 1, "Cron End");
        definition.getNodes().getFirst().getMetadata().put("triggerType", "CRON");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("START cron trigger requires cron metadata", exception.getMessage());
    }

    @Test
    @DisplayName("WEBHOOK 시작 노드는 webhookToken metadata가 필요하다")
    void saveRequiresWebhookTokenForWebhookTrigger() {
        WorkflowDefinitionService service = workflowDefinitionService();
        WorkflowDefinition definition = definition("webhook-flow", 1, "Webhook End");
        definition.getNodes().getFirst().getMetadata().put("triggerType", "WEBHOOK");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(definition)
        );

        assertEquals("START webhook trigger requires webhookToken metadata", exception.getMessage());
    }

    private WorkflowDefinitionService workflowDefinitionService() {
        return new WorkflowDefinitionService(
                new WorkflowDefinitionRepository(),
                new NodeExecutorRegistry(java.util.List.of(new StartNodeExecutor(), new EndNodeExecutor()))
        );
    }

    private WorkflowDefinition definition(String workflowId, int version, String endNodeName) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(workflowId);
        definition.setVersion(version);
        definition.setName("Workflow " + workflowId + " v" + version);
        definition.setNodes(java.util.List.of(
                node("node_start", "Start", com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType.START),
                node("node_end", endNodeName, com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType.END)
        ));

        com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnections startConnections =
                new com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnections();
        startConnections.setMain(java.util.List.of(java.util.List.of(
                target("node_end", 0)
        )));

        java.util.Map<String, com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnections> connections =
                new java.util.LinkedHashMap<>();
        connections.put("node_start", startConnections);
        definition.setConnections(connections);
        return definition;
    }

    private com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node node(
            String id,
            String name,
            com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType type
    ) {
        com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node node =
                new com.luziatcode.demoworkflowengine.service.workflow.domain.definition.Node();
        node.setId(id);
        node.setName(name);
        node.setType(type);
        return node;
    }

    private com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnectionTarget target(
            String nodeId,
            int index
    ) {
        com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnectionTarget target =
                new com.luziatcode.demoworkflowengine.service.workflow.domain.definition.NodeConnectionTarget();
        target.setNodeId(nodeId);
        target.setType("main");
        target.setIndex(index);
        return target;
    }
}
