package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class IntegrationService {
    private final PlatformStore store;
    private final SeaTunnelConfigBuilder builder;
    private final SeaTunnelGateway gateway;
    private final ObjectMapper mapper;
    private final PasswordCipher passwordCipher;

    public IntegrationService(PlatformStore store, SeaTunnelConfigBuilder builder, SeaTunnelGateway gateway,
                              ObjectMapper mapper, PasswordCipher passwordCipher) {
        this.store = store;
        this.builder = builder;
        this.gateway = gateway;
        this.mapper = mapper;
        this.passwordCipher = passwordCipher;
    }
    public List<IntegrationTaskView> list() { return store.integrationTasks.values().stream().map(this::masked).toList(); }
    public IntegrationTaskView create(IntegrationRequests.TaskRequest request) {
        List<IntegrationRequests.TableRequest> tables = resolveTables(request, null);
        IntegrationTask task = new IntegrationTask(request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), request.source(), request.target(), request.mappings(), request.options(), tables);
        long id = store.nextId();
        IntegrationTaskView view = new IntegrationTaskView(id, request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), "DRAFT", secureEndpoint(request.source()), secureEndpoint(request.target()),
                secureTransform(request.mappings(), request.options(), tables), safeConfig(task), tableViews(id, tables));
        store.integrationTasks.put(id, view);
        store.integrationTaskTables.put(id, view.tables());
        store.persistIntegrationTask(view);
        return masked(view);
    }
    public IntegrationTaskView update(long id, IntegrationRequests.TaskRequest request) {
        IntegrationTaskView currentView = raw(id);
        IntegrationTask current = hasStructuredConfig(currentView) ? task(id) : null;
        IntegrationRequests.Endpoint source = preservePassword(request.source(), current == null ? null : current.source());
        IntegrationRequests.Endpoint target = preservePassword(request.target(), current == null ? null : current.target());
        List<IntegrationRequests.TableRequest> tables = resolveTables(request, current);
        IntegrationTask task = new IntegrationTask(request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), source, target, request.mappings(), request.options(), tables);
        IntegrationTaskView view = new IntegrationTaskView(id, request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), "DRAFT", secureEndpoint(source), secureEndpoint(target),
                secureTransform(request.mappings(), request.options(), tables), safeConfig(task), tableViews(id, tables));
        store.integrationTasks.put(id, view);
        store.integrationTaskTables.put(id, view.tables());
        store.persistIntegrationTask(view);
        return masked(view);
    }
    public IntegrationTaskView get(long id) {
        IntegrationTaskView view = store.integrationTasks.get(id);
        if (view == null) throw new NotFoundException("离线同步任务不存在：" + id);
        return masked(view);
    }
    public void delete(long id) { if (store.integrationTasks.remove(id) == null) throw new NotFoundException("离线同步任务不存在：" + id); store.integrationTaskTables.remove(id); store.deleteIntegrationTask(id); }
    public SeaTunnelGateway.ValidationResult validate(long id) {
        IntegrationTaskView view = raw(id);
        return gateway.validate(hasStructuredConfig(view) ? builder.build(task(id)) : view.seatunnelConfig());
    }
    public SeaTunnelGateway.SubmitResult execute(long id) {
        IntegrationTaskView view = raw(id);
        SeaTunnelGateway.SubmitResult result = gateway.submit(hasStructuredConfig(view) ? builder.build(task(id)) : view.seatunnelConfig());
        long instanceId = store.nextId();
        IntegrationInstanceView instance = new IntegrationInstanceView(instanceId, id, result.executionId(), result.status(), LocalDateTime.now(),
                "SUCCESS".equals(result.status()) ? LocalDateTime.now() : null, "SeaTunnel 执行实例");
        store.integrationInstances.put(instanceId, instance);
        store.persistIntegrationInstance(instance);
        return result;
    }
    public void stop(String executionId) { gateway.cancel(executionId); }
    public List<IntegrationInstanceView> instances(long taskId) { return store.integrationInstances.values().stream().filter(item -> item.taskId() == taskId).toList(); }
    public List<IntegrationTableView> tables(long taskId) {
        raw(taskId);
        List<IntegrationTableView> persisted = store.integrationTaskTables.getOrDefault(taskId, List.of());
        return persisted.isEmpty() ? legacyTable(raw(taskId)) : persisted;
    }
    public IntegrationTaskView deleteTable(long taskId, long tableId) {
        IntegrationTaskView currentView = raw(taskId);
        if (instances(taskId).stream().anyMatch(item -> "RUNNING".equalsIgnoreCase(item.status()))) {
            throw new BadRequestException("任务运行中，不能删除表");
        }
        List<IntegrationTableView> currentTables = tables(taskId);
        IntegrationTableView removed = currentTables.stream().filter(item -> item.id() == tableId).findFirst()
                .orElseThrow(() -> new NotFoundException("同步任务表不存在：" + tableId));
        List<IntegrationTableView> remaining = currentTables.stream().filter(item -> item.id() != removed.id()).toList();
        IntegrationTask task = task(taskId);
        String config = remaining.isEmpty() ? "" : safeConfig(new IntegrationTask(task.name(), task.sourceType(), task.targetType(), task.syncMode(), task.source(), task.target(), task.mappings(), task.options(), toRequests(remaining)));
        IntegrationRequests.Endpoint remainingSource = remaining.isEmpty() ? withTable(task.source(), "") : withTable(task.source(), remaining.get(0).sourceTable());
        IntegrationRequests.Endpoint remainingTarget = remaining.isEmpty() ? withTable(task.target(), "") : withTable(task.target(), remaining.get(0).targetTable());
        IntegrationTaskView updated = new IntegrationTaskView(currentView.id(), currentView.name(), currentView.sourceType(), currentView.targetType(),
                currentView.syncMode(), "DRAFT", secureEndpoint(remainingSource), secureEndpoint(remainingTarget),
                secureTransform(task.mappings(), task.options(), toRequests(remaining)), config, remaining);
        store.integrationTasks.put(taskId, updated);
        store.integrationTaskTables.put(taskId, remaining);
        store.persistIntegrationTask(updated);
        return masked(updated);
    }
    public SeaTunnelGateway.JobStatus status(String executionId) {
        SeaTunnelGateway.JobStatus status = gateway.status(executionId);
        store.integrationInstances.values().stream().filter(item -> executionId.equals(item.executionId())).findFirst().ifPresent(instance -> {
            IntegrationInstanceView updated = new IntegrationInstanceView(instance.id(), instance.taskId(), instance.executionId(),
                    status.status(), instance.startedAt(), "RUNNING".equals(status.status()) ? null : LocalDateTime.now(), status.message());
            store.integrationInstances.put(instance.id(), updated);
            store.persistIntegrationInstance(updated);
        });
        return status;
    }
    public String log(String executionId) { return gateway.log(executionId); }
    public void cancel(String executionId) { gateway.cancel(executionId); }

    private IntegrationTaskView raw(long id) {
        IntegrationTaskView view = store.integrationTasks.get(id);
        if (view == null) throw new NotFoundException("离线同步任务不存在：" + id);
        return view;
    }
    private IntegrationTaskView masked(IntegrationTaskView view) {
        String masked = view.seatunnelConfig() == null ? null : view.seatunnelConfig()
                .replaceAll("(\"password\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?m)(password\\s*=\\s*\")[^\"]*(\")", "$1***$2");
        List<IntegrationTableView> tables = view.tables() == null || view.tables().isEmpty() ? legacyTable(view) : view.tables();
        return new IntegrationTaskView(view.id(), view.name(), view.sourceType(), view.targetType(), view.syncMode(), view.status(),
                maskJson(view.sourceConfigJson()), maskJson(view.targetConfigJson()), view.transformConfigJson(), masked, tables);
    }

    private IntegrationTask task(long id) {
        IntegrationTaskView view = raw(id);
        try {
            if (view.sourceConfigJson() == null || view.sourceConfigJson().isBlank() || "{}".equals(view.sourceConfigJson().trim())) {
                throw new BadRequestException("同步任务缺少结构化来源配置，请重新保存任务");
            }
            var sourceStored = mapper.readValue(view.sourceConfigJson(), IntegrationRequests.Endpoint.class);
            var targetStored = mapper.readValue(view.targetConfigJson(), IntegrationRequests.Endpoint.class);
            var transform = mapper.readTree(view.transformConfigJson() == null ? "{}" : view.transformConfigJson());
            var mappings = mapper.convertValue(transform.path("mappings"), new TypeReference<List<IntegrationRequests.FieldMapping>>() { });
            var options = mapper.convertValue(transform.path("options"), new TypeReference<java.util.Map<String, Object>>() { });
            List<IntegrationRequests.TableRequest> tables = null;
            if (transform.has("tables")) tables = mapper.convertValue(transform.path("tables"), new TypeReference<List<IntegrationRequests.TableRequest>>() { });
            var source = withPassword(sourceStored, passwordCipher.decrypt(sourceStored.password()));
            var target = withPassword(targetStored, passwordCipher.decrypt(targetStored.password()));
            return new IntegrationTask(view.name(), view.sourceType(), view.targetType(), view.syncMode(), source, target, mappings, options, tables);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("同步任务配置无法读取，请重新保存任务");
        }
    }

    private boolean hasStructuredConfig(IntegrationTaskView view) {
        return view.sourceConfigJson() != null && !view.sourceConfigJson().isBlank() && !"{}".equals(view.sourceConfigJson().trim());
    }

    private IntegrationRequests.Endpoint withPassword(IntegrationRequests.Endpoint endpoint, String password) {
        return new IntegrationRequests.Endpoint(endpoint.host(), endpoint.port(), endpoint.database(), endpoint.username(), password, endpoint.table());
    }
    private IntegrationRequests.Endpoint withTable(IntegrationRequests.Endpoint endpoint, String table) {
        return new IntegrationRequests.Endpoint(endpoint.host(), endpoint.port(), endpoint.database(), endpoint.username(), endpoint.password(), table);
    }
    private IntegrationRequests.Endpoint preservePassword(IntegrationRequests.Endpoint incoming, IntegrationRequests.Endpoint current) {
        if (current == null || incoming == null || (incoming.password() != null && !incoming.password().isBlank() && !"***".equals(incoming.password()))) return incoming;
        return withPassword(incoming, current.password());
    }
    private String secureEndpoint(IntegrationRequests.Endpoint endpoint) {
        try {
            ObjectNode node = mapper.valueToTree(endpoint);
            node.put("password", passwordCipher.encrypt(endpoint.password()));
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) { throw new BadRequestException("同步任务密码无法安全保存"); }
    }
    private String secureTransform(List<IntegrationRequests.FieldMapping> mappings, Map<String, Object> options,
                                   List<IntegrationRequests.TableRequest> tables) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.set("mappings", mapper.valueToTree(mappings == null ? List.of() : mappings));
            node.set("options", mapper.valueToTree(options == null ? java.util.Map.of() : options));
            if (tables != null) node.set("tables", mapper.valueToTree(tables));
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) { throw new BadRequestException("同步任务转换配置无法保存"); }
    }
    private String safeConfig(IntegrationTask task) {
        IntegrationRequests.Endpoint source = withPassword(task.source(), "***");
        IntegrationRequests.Endpoint target = withPassword(task.target(), "***");
        return builder.build(new IntegrationTask(task.name(), task.sourceType(), task.targetType(), task.syncMode(), source, target, task.mappings(), task.options(), task.tables()));
    }
    private String maskJson(String json) {
        return json == null ? "{}" : json.replaceAll("(\"password\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
    }

    private List<IntegrationRequests.TableRequest> resolveTables(IntegrationRequests.TaskRequest request, IntegrationTask current) {
        if (request.tables() != null && !request.tables().isEmpty()) return request.tables();
        if (request.source() != null && request.target() != null && request.source().table() != null && !request.source().table().isBlank()
                && request.target().table() != null && !request.target().table().isBlank()) {
            return List.of(new IntegrationRequests.TableRequest(request.source().database(), request.source().table(),
                    request.target().database(), request.target().table(), ""));
        }
        if (current != null && current.tables() != null) return current.tables();
        throw new BadRequestException("至少配置一张源表和目标表");
    }

    private List<IntegrationTableView> tableViews(long taskId, List<IntegrationRequests.TableRequest> tables) {
        if (tables == null) return List.of();
        List<IntegrationTableView> result = new ArrayList<>();
        for (IntegrationRequests.TableRequest table : tables) {
            result.add(new IntegrationTableView(store.nextId(), taskId, table.sourceDatabase(), table.sourceTable(),
                    table.targetDatabase(), table.targetTable(), table.partitionColumn()));
        }
        return result;
    }

    private List<IntegrationRequests.TableRequest> toRequests(List<IntegrationTableView> tables) {
        return tables.stream().map(table -> new IntegrationRequests.TableRequest(table.sourceDatabase(), table.sourceTable(),
                table.targetDatabase(), table.targetTable(), table.partitionColumn())).toList();
    }

    private List<IntegrationTableView> legacyTable(IntegrationTaskView view) {
        if (!hasStructuredConfig(view)) return List.of();
        try {
            IntegrationRequests.Endpoint source = mapper.readValue(view.sourceConfigJson(), IntegrationRequests.Endpoint.class);
            IntegrationRequests.Endpoint target = mapper.readValue(view.targetConfigJson(), IntegrationRequests.Endpoint.class);
            if (source.table() == null || source.table().isBlank() || target.table() == null || target.table().isBlank()) return List.of();
            return List.of(new IntegrationTableView(view.id(), view.id(), source.database(), source.table(), target.database(), target.table(), ""));
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
