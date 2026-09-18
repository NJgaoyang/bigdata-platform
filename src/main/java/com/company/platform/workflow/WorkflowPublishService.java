package com.company.platform.workflow;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.development.DevFileView;
import com.company.platform.development.FileVersionView;
import com.company.platform.lineage.LineageService;
import com.company.platform.scheduler.SchedulerGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class WorkflowPublishService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowPublishService.class);
    private final WorkflowService workflowService;
    private final SchedulerGateway schedulerGateway;
    private final PlatformStore store;
    private final LineageService lineageService;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorkflowPublishService(WorkflowService workflowService, SchedulerGateway schedulerGateway, PlatformStore store,
                                  LineageService lineageService) {
        this.workflowService = workflowService;
        this.schedulerGateway = schedulerGateway;
        this.store = store;
        this.lineageService = lineageService;
    }

    @Transactional
    public PublishResult publish(long workflowId) {
        WorkflowView workflow = workflowService.get(workflowId);
        DagValidator.ValidationResult validation = workflowService.validate(workflowId);
        if (!validation.valid()) throw new BadRequestException(validation.message());
        int version = workflow.publishedVersion() + 1;
        ObjectNode definition = mapper.createObjectNode();
        definition.put("workflowCode", workflow.workflowCode());
        definition.put("name", workflow.name());
        definition.put("description", workflow.description() == null ? "" : workflow.description());
        definition.put("version", version);
        ArrayNode nodes = definition.putArray("nodes");
        List<FileVersionView> sqlVersions = new ArrayList<>();

        for (WorkflowNodeView node : workflow.nodes()) {
            boolean taskNode = node.nodeType() != NodeType.SEATUNNEL && node.nodeType() != NodeType.CONDITION;
            if (taskNode && node.devFileId() == null) {
                throw new BadRequestException("节点“" + node.name() + "”没有绑定开发任务");
            }
            String snapshot = "";
            NodeType effectiveType = node.nodeType();
            FileVersionView effectiveVersion = null;
            DevFileView devFile = null;
            // Workflow nodes bind a development task. Publishing freezes that task's current production Vx.
            if (node.devFileId() != null) {
                devFile = store.files.get(node.devFileId());
                if (devFile == null) throw new BadRequestException("节点“" + node.name() + "”绑定的开发任务不存在");
                String boundTaskName = devFile.name();
                effectiveVersion = store.versions.values().stream()
                        .filter(v -> v.fileId() == node.devFileId() && v.publishFlag())
                        .max(java.util.Comparator.comparingInt(FileVersionView::versionNo))
                        .orElseThrow(() -> new BadRequestException("开发任务“" + boundTaskName + "”还没有生产版本，请先发布任务"));
            }
            if (effectiveVersion != null && devFile != null) {
                effectiveType = node.nodeType() == NodeType.CONDITION ? NodeType.CONDITION : resolveTaskType(devFile.fileType(), devFile.name());
                snapshot = Base64.getEncoder().encodeToString(effectiveVersion.content().getBytes(StandardCharsets.UTF_8));
                if (effectiveType == NodeType.SQL) sqlVersions.add(effectiveVersion);
            }
            ObjectNode item = nodes.addObject();
            item.put("id", node.id());
            item.put("name", node.name());
            item.put("type", effectiveType.name());
            item.put("configJson", node.configJson() == null ? "" : node.configJson());
            if (node.devFileId() == null) item.putNull("devFileId"); else item.put("devFileId", node.devFileId());
            if (effectiveVersion == null) item.putNull("fileVersionId"); else item.put("fileVersionId", effectiveVersion.id());
            if (effectiveVersion == null) item.putNull("taskVersion"); else item.put("taskVersion", effectiveVersion.versionNo());
            item.put("contentBase64", snapshot);
            item.put("x", node.x());
            item.put("y", node.y());
        }

        ArrayNode edges = definition.putArray("edges");
        for (WorkflowEdgeView edge : workflow.edges()) {
            ObjectNode item = edges.addObject();
            item.put("sourceNodeId", edge.sourceNodeId());
            item.put("targetNodeId", edge.targetNodeId());
        }

        String definitionJson;
        try { definitionJson = mapper.writeValueAsString(definition); }
        catch (JsonProcessingException ex) { throw new BadRequestException("工作流发布快照生成失败"); }

        SchedulerGateway.PublishResult result = schedulerGateway.publish(
                new SchedulerGateway.PublishRequest(workflow.workflowCode(), workflow.name(), version, definitionJson, workflow.dsProcessCode()));
        // A DolphinScheduler update keeps the original process-definition code. Do not replace
        // it with an unrelated id/code field that may be present in the update response.
        String effectiveProcessCode = workflow.dsProcessCode() == null || workflow.dsProcessCode().isBlank()
                ? result.processCode() : workflow.dsProcessCode();
        // Creating/updating a process definition does not make it runnable in DolphinScheduler.
        // The platform's "发布" action means the new version is ready for execution, so the
        // DS definition must be released ONLINE before we persist PUBLISHED locally.
        schedulerGateway.release(effectiveProcessCode, true);
        WorkflowView published = new WorkflowView(workflow.id(), workflow.name(), workflow.workflowCode(), workflow.description(),
                "PUBLISHED", version, workflow.nodes(), workflow.edges(), effectiveProcessCode);
        store.persistWorkflow(published);
        store.workflows.put(workflowId, published);

        boolean lineageWarning = false;
        for (FileVersionView fileVersion : sqlVersions) {
            try {
                lineageService.removeForFile(fileVersion.fileId());
                lineageService.parseAndStore(fileVersion.fileId(), fileVersion.id(), fileVersion.content());
            } catch (RuntimeException ex) {
                lineageWarning = true;
                log.warn("SQL lineage refresh failed after workflow {} version {} publish, fileVersion={}",
                        workflow.workflowCode(), version, fileVersion.id(), ex);
            }
        }
        String message = lineageWarning
                ? "已发布到平台调度；部分 SQL 血缘解析失败，请检查日志"
                : "已发布到平台调度";
        return new PublishResult(workflow.id(), version, effectiveProcessCode, result.status(), message);
    }

    public RunResult run(long workflowId) {
        WorkflowView workflow = workflowService.get(workflowId);
        if (!"PUBLISHED".equals(workflow.status())) throw new BadRequestException("工作流必须发布后才能运行");
        String processCode = workflow.dsProcessCode() == null || workflow.dsProcessCode().isBlank()
                ? workflow.workflowCode() : workflow.dsProcessCode();
        SchedulerGateway.RunResult result = schedulerGateway.run(processCode);
        return new RunResult(result.instanceId(), result.status(), "调度实例已提交");
    }

    private NodeType resolveTaskType(String fileType, String fileName) {
        String normalized = fileType == null ? "" : fileType.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(".")) normalized = normalized.substring(1);
        return switch (normalized) {
            case "SQL" -> NodeType.SQL;
            case "PY", "PYTHON", "PYTHON3" -> NodeType.PYTHON;
            case "SH", "SHELL", "BASH" -> NodeType.SHELL;
            case "SEATUNNEL", "HOCON", "CONF" -> NodeType.SEATUNNEL;
            default -> resolveTaskTypeFromFileName(fileName, fileType);
        };
    }

    private NodeType resolveTaskTypeFromFileName(String fileName, String configuredType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".sql")) return NodeType.SQL;
        if (name.endsWith(".py")) return NodeType.PYTHON;
        if (name.endsWith(".sh") || name.endsWith(".bash")) return NodeType.SHELL;
        if (name.endsWith(".conf") || name.endsWith(".hocon") || name.endsWith(".seatunnel")) return NodeType.SEATUNNEL;
        throw new BadRequestException("不支持的开发文件类型：" + (configuredType == null ? "" : configuredType)
                + "，文件：" + (fileName == null ? "" : fileName));
    }

    public record PublishResult(long workflowId, int version, String processCode, String status, String message) { }
    public record RunResult(String instanceId, String status, String message) { }
}
