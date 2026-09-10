package com.company.platform.workflow;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.scheduler.SchedulerGateway;
import com.company.platform.lineage.LineageService;
import com.company.platform.development.DevFileView;
import com.company.platform.development.FileVersionView;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.StringJoiner;

@Service
public class WorkflowPublishService {
    private final WorkflowService workflowService;
    private final SchedulerGateway schedulerGateway;
    private final PlatformStore store;
    private final LineageService lineageService;
    public WorkflowPublishService(WorkflowService workflowService, SchedulerGateway schedulerGateway, PlatformStore store,
                                  LineageService lineageService) {
        this.workflowService = workflowService; this.schedulerGateway = schedulerGateway; this.store = store; this.lineageService = lineageService;
    }
    public PublishResult publish(long workflowId) {
        WorkflowView workflow = workflowService.get(workflowId);
        DagValidator.ValidationResult validation = workflowService.validate(workflowId);
        if (!validation.valid()) throw new BadRequestException(validation.message());
        int version = workflow.publishedVersion() + 1;
        StringJoiner nodeDefinitions = new StringJoiner(",", "[", "]");
        for (WorkflowNodeView node : workflow.nodes()) {
            if (node.nodeType() != NodeType.SEATUNNEL && node.fileVersionId() == null) {
                throw new BadRequestException("节点“" + node.name() + "”没有绑定开发文件版本");
            }
            String snapshot = "";
            NodeType effectiveType = node.nodeType();
            if (node.fileVersionId() != null) {
                FileVersionView fileVersion = store.versions.get(node.fileVersionId());
                if (fileVersion == null) throw new BadRequestException("节点“" + node.name() + "”绑定的文件版本不存在");
                DevFileView devFile = store.files.get(fileVersion.fileId());
                if (devFile == null) throw new BadRequestException("节点“" + node.name() + "”绑定的开发文件不存在");
                effectiveType = resolveTaskType(devFile.fileType(), devFile.name());
                snapshot = Base64.getEncoder().encodeToString(fileVersion.content().getBytes(StandardCharsets.UTF_8));
                if (effectiveType == NodeType.SQL) {
                    lineageService.removeForFile(fileVersion.fileId());
                    lineageService.parseAndStore(fileVersion.fileId(), fileVersion.id(), fileVersion.content());
                }
            }
            nodeDefinitions.add("{\"id\":" + node.id() + ",\"name\":\"" + escape(node.name()) + "\",\"type\":\"" + effectiveType + "\",\"configJson\":\"" +
                    escape(node.configJson() == null ? "" : node.configJson()) + "\",\"fileVersionId\":" +
                    (node.fileVersionId() == null ? "null" : node.fileVersionId()) + ",\"contentBase64\":\"" + snapshot + "\"}");
        }
        StringJoiner edgeDefinitions = new StringJoiner(",", "[", "]");
        for (WorkflowEdgeView edge : workflow.edges()) {
            edgeDefinitions.add("{\"sourceNodeId\":" + edge.sourceNodeId() + ",\"targetNodeId\":" + edge.targetNodeId() + "}");
        }
        String definition = "{\"workflowCode\":\"" + escape(workflow.workflowCode()) + "\",\"name\":\"" + escape(workflow.name()) +
                "\",\"description\":\"" + escape(workflow.description() == null ? "" : workflow.description()) + "\",\"version\":" + version +
                ",\"nodes\":" + nodeDefinitions + ",\"edges\":" + edgeDefinitions + "}";
        SchedulerGateway.PublishResult result = schedulerGateway.publish(
                new SchedulerGateway.PublishRequest(workflow.workflowCode(), workflow.name(), version, definition, workflow.dsProcessCode()));
        store.workflows.put(workflowId, new WorkflowView(workflow.id(), workflow.name(), workflow.workflowCode(),
                workflow.description(), "PUBLISHED", version, workflow.nodes(), workflow.edges(), result.processCode()));
        store.persistWorkflow(store.workflows.get(workflowId));
        return new PublishResult(workflow.id(), version, result.processCode(), result.status(), "已按开发文件类型生成 DolphinScheduler 发布快照");
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

    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    public record PublishResult(long workflowId, int version, String processCode, String status, String message) { }
    public record RunResult(String instanceId, String status, String message) { }
}
