package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.company.platform.datasource.DataSourceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IntegrationService {
    private final PlatformStore store;
    private final SeaTunnelConfigBuilder builder;
    private final SeaTunnelGateway gateway;
    private final ObjectMapper mapper;
    private final PasswordCipher passwordCipher;
    private final DataSourceService dataSourceService;
    private final StarRocksSchemaService schemaService;
    private final IntegrationRuntimeRepository runtimeRepository;
    private final IntegrationPreCheckService preCheckService;
    private final IntegrationStagingService stagingService;

    /** Retained for existing focused unit tests. */
    public IntegrationService(PlatformStore store, SeaTunnelConfigBuilder builder, SeaTunnelGateway gateway,
                              ObjectMapper mapper, PasswordCipher passwordCipher, DataSourceService dataSourceService) {
        this(store, builder, gateway, mapper, passwordCipher, dataSourceService, null, null, null, null);
    }

    /** Backwards-compatible constructor for batch runtime focused tests. */
    public IntegrationService(PlatformStore store, SeaTunnelConfigBuilder builder, SeaTunnelGateway gateway,
                              ObjectMapper mapper, PasswordCipher passwordCipher, DataSourceService dataSourceService,
                              StarRocksSchemaService schemaService, IntegrationRuntimeRepository runtimeRepository) {
        this(store, builder, gateway, mapper, passwordCipher, dataSourceService, schemaService, runtimeRepository, null, null);
    }

    @Autowired
    public IntegrationService(PlatformStore store, SeaTunnelConfigBuilder builder, SeaTunnelGateway gateway,
                              ObjectMapper mapper, PasswordCipher passwordCipher, DataSourceService dataSourceService,
                              StarRocksSchemaService schemaService, IntegrationRuntimeRepository runtimeRepository,
                              IntegrationPreCheckService preCheckService, IntegrationStagingService stagingService) {
        this.store = store;
        this.builder = builder;
        this.gateway = gateway;
        this.mapper = mapper;
        this.passwordCipher = passwordCipher;
        this.dataSourceService = dataSourceService;
        this.schemaService = schemaService;
        this.runtimeRepository = runtimeRepository;
        this.preCheckService = preCheckService;
        this.stagingService = stagingService;
    }

    public List<IntegrationTaskView> list() {
        return store.integrationTasks.values().stream().sorted(Comparator.comparingLong(IntegrationTaskView::id).reversed())
                .map(this::masked).toList();
    }

    @Transactional
    public IntegrationTaskView create(IntegrationRequests.TaskRequest request) {
        Map<String, Object> options = effectiveOptions(request.options(), request.sourceDataSourceId(), request.targetDataSourceId());
        validateMode(request.syncMode(), options);
        List<IntegrationRequests.TableRequest> tables = resolveTables(request, null);
        IntegrationRequests.Endpoint source = resolveDataSource(request.sourceDataSourceId(), request.source());
        IntegrationRequests.Endpoint target = resolveDataSource(request.targetDataSourceId(), request.target());
        IntegrationTask task = new IntegrationTask(request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), source, target, request.mappings(), options, tables);
        builder.build(task);
        long id = store.nextId();
        IntegrationTaskView view = new IntegrationTaskView(id, request.name(), request.sourceType(), request.targetType(),
                normalizeMode(request.syncMode()), "GENERATED", "OFFLINE", secureEndpoint(source), secureEndpoint(target),
                secureTransform(request.mappings(), options, tables), safeConfig(task), tableViews(id, tables));
        store.persistIntegrationTask(view);
        store.integrationTasks.put(id, view);
        store.integrationTaskTables.put(id, view.tables());
        return masked(view);
    }

    @Transactional
    public IntegrationTaskView update(long id, IntegrationRequests.TaskRequest request) {
        Map<String, Object> options = effectiveOptions(request.options(), request.sourceDataSourceId(), request.targetDataSourceId());
        validateMode(request.syncMode(), options);
        IntegrationTaskView currentView = raw(id);
        ensureOffline(currentView);
        IntegrationTask current = hasStructuredConfig(currentView) ? task(id) : null;
        IntegrationRequests.Endpoint source = request.sourceDataSourceId() == null
                ? preservePassword(request.source(), current == null ? null : current.source())
                : resolveDataSource(request.sourceDataSourceId(), request.source());
        IntegrationRequests.Endpoint target = request.targetDataSourceId() == null
                ? preservePassword(request.target(), current == null ? null : current.target())
                : resolveDataSource(request.targetDataSourceId(), request.target());
        List<IntegrationRequests.TableRequest> tables = resolveTables(request, current);
        IntegrationTask task = new IntegrationTask(request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), source, target, request.mappings(), options, tables);
        builder.build(task);
        IntegrationTaskView view = new IntegrationTaskView(id, request.name(), request.sourceType(), request.targetType(),
                normalizeMode(request.syncMode()), currentView.status(), currentView.lifecycleStatus(), secureEndpoint(source), secureEndpoint(target),
                secureTransform(request.mappings(), options, tables), safeConfig(task), tableViews(id, tables));
        store.persistIntegrationTask(view);
        store.integrationTasks.put(id, view);
        store.integrationTaskTables.put(id, view.tables());
        return masked(view);
    }

    public IntegrationTaskView get(long id) {
        IntegrationTaskView view = store.integrationTasks.get(id);
        if (view == null) throw new NotFoundException("同步任务不存在：" + id);
        return masked(view);
    }

    @Transactional
    public IntegrationTaskView online(long id) {
        IntegrationTaskView current = raw(id);
        if (isOnline(current)) return masked(current);
        SeaTunnelGateway.ValidationResult validation = validate(id);
        if (!validation.valid()) throw new BadRequestException(validation.message() == null || validation.message().isBlank()
                ? "任务配置校验失败，无法上线" : validation.message());
        IntegrationPreCheckService.Report report = precheck(id);
        if (!report.allowed()) {
            String detail = report.items().stream().filter(IntegrationPreCheckService.Item::blocking)
                    .map(item -> item.message() + (item.detail() == null || item.detail().isBlank() ? "" : "（" + item.detail() + "）"))
                    .reduce((a,b) -> a + "；" + b).orElse("离线任务发布前检查失败");
            throw new BadRequestException(detail);
        }
        return updateLifecycle(current, "ONLINE");
    }

    @Transactional
    public IntegrationTaskView offline(long id) {
        IntegrationTaskView current = raw(id);
        if (!isOnline(current)) return masked(current);
        if (instances(id).stream().anyMatch(item -> active(item.status()))) {
            throw new BadRequestException("任务正在运行，请停止后再下线");
        }
        return updateLifecycle(current, "OFFLINE");
    }

    @Transactional
    public void delete(long id) {
        IntegrationTaskView current = raw(id);
        ensureOffline(current);
        if (instances(id).stream().anyMatch(item -> active(item.status()))) {
            throw new BadRequestException("任务运行中，请先停止任务再删除");
        }
        store.deleteIntegrationTask(id);
        store.integrationTasks.remove(id);
        store.integrationTaskTables.remove(id);
    }

    public SeaTunnelGateway.ValidationResult validate(long id) {
        IntegrationTaskView view = raw(id);
        if (!hasStructuredConfig(view)) return gateway.validate(view.seatunnelConfig(), runtimeClusterId());
        IntegrationTask runtimeTask = task(id);
        return gateway.validate(builder.build(runtimeTask), runtimeClusterId());
    }


    public IntegrationPreCheckService.Report precheck(long id) {
        IntegrationTask runtimeTask = task(id);
        if (preCheckService == null) return new IntegrationPreCheckService.Report(true, List.of());
        return preCheckService.check(runtimeTask);
    }

    public String previewConfig(IntegrationRequests.TaskRequest request) {
        Map<String, Object> options = effectiveOptions(request.options(), request.sourceDataSourceId(), request.targetDataSourceId());
        validateMode(request.syncMode(), options);
        List<IntegrationRequests.TableRequest> tables = resolveTables(request, null);
        IntegrationRequests.Endpoint source = resolveDataSource(request.sourceDataSourceId(), request.source());
        IntegrationRequests.Endpoint target = resolveDataSource(request.targetDataSourceId(), request.target());
        IntegrationTask task = new IntegrationTask(request.name(), request.sourceType(), request.targetType(), request.syncMode(),
                source, target, request.mappings(), options, tables);
        return safeConfig(task);
    }

    public List<StarRocksSchemaService.SchemaResult> syncSchema(long id, boolean recreate) {
        if (schemaService == null) throw new BadRequestException("StarRocks 表结构同步组件不可用");
        IntegrationTask runtimeTask = task(id);
        if (!recreate) return schemaService.prepare(runtimeTask);
        Map<String, Object> options = new HashMap<>(runtimeTask.options() == null ? Map.of() : runtimeTask.options());
        options.put("schemaSaveMode", "RECREATE_SCHEMA");
        return schemaService.prepare(new IntegrationTask(runtimeTask.name(), runtimeTask.sourceType(), runtimeTask.targetType(),
                runtimeTask.syncMode(), runtimeTask.source(), runtimeTask.target(), runtimeTask.mappings(), options, runtimeTask.tables()));
    }

    public SeaTunnelGateway.SubmitResult execute(long id) {
        return execute(id, "MANUAL");
    }

    public SeaTunnelGateway.SubmitResult execute(long id, String triggerType) {
        IntegrationTaskView view = raw(id);
        String normalizedTrigger = normalizeTrigger(triggerType);
        if (!isOnline(view)) {
            throw new BadRequestException("离线同步任务已下线，请先上线后再运行");
        }
        IntegrationTask runtimeTask = hasStructuredConfig(view) ? task(id) : null;
        Long clusterId = runtimeClusterId();
        if (runtimeTask != null && stagingService != null && stagingService.requiresStaging(runtimeTask)) {
            IntegrationStagingService.Prepared staged = stagingService.prepare(runtimeTask);
            String parameters = stagingService.parametersJson(staged.mappings(), "FULL_OVERWRITE");
            BatchExecution execution = submitNewBatch(id, normalizedTrigger, parameters, null, staged.task(), builder.build(staged.task()), clusterId);
            return execution.result();
        }
        String config = runtimeTask == null ? view.seatunnelConfig() : builder.build(runtimeTask);
        BatchExecution execution = submitNewBatch(id, normalizedTrigger, "{}", null, runtimeTask, config, clusterId);
        return execution.result();
    }

    public IntegrationBatchView backfill(long id, IntegrationRequests.BackfillRequest request) {
        IntegrationTaskView view = raw(id);
        if (!isOnline(view)) throw new BadRequestException("离线同步任务已下线，请先上线后再补数");
        IntegrationTask runtimeTask = task(id);
        Map<String, Object> options = new HashMap<>(runtimeTask.options() == null ? Map.of() : runtimeTask.options());
        options.put("where", request.where().trim());
        options.put("targetPolicy", "APPEND_NO_SCHEMA");
        options.put("schemaSaveMode", "ERROR_WHEN_SCHEMA_NOT_EXIST");
        options.put("dataSaveMode", "APPEND_DATA");
        IntegrationTask backfillTask = new IntegrationTask(runtimeTask.name(), runtimeTask.sourceType(), runtimeTask.targetType(),
                "INCREMENTAL", runtimeTask.source(), runtimeTask.target(), runtimeTask.mappings(), options, runtimeTask.tables());
        String parameters = json(Map.of("where", request.where().trim(),
                "startLabel", value(request.startLabel()), "endLabel", value(request.endLabel())));
        return submitNewBatch(id, "BACKFILL", parameters, null, backfillTask, builder.build(backfillTask),
                runtimeClusterId()).batch();
    }

    public IntegrationBatchView retryBatch(long batchId) {
        ensureRuntimeRepository();
        IntegrationBatchView batch = runtimeRepository.getBatch(batchId);
        IntegrationTaskView task = raw(batch.taskId());
        if (!isOnline(task)) throw new BadRequestException("离线同步任务已下线，请先上线后再重试");
        IntegrationAttemptView latest = runtimeRepository.latestAttempt(batchId);
        if (latest != null && active(latest.status())) throw new BadRequestException("该批次仍在运行，不能重复重试");
        String config = runtimeRepository.runtimeConfig(batchId);
        if (config != null && config.contains("data_save_mode = \"APPEND_DATA\"")) {
            throw new BadRequestException("该历史批次使用仅追加写入，直接重试可能产生重复数据；请改用全量覆盖/主键幂等策略后重新运行");
        }
        runtimeRepository.updateBatch(batchId, "QUEUED", null, null);
        submitExistingBatch(batch, config);
        return runtimeRepository.getBatch(batchId);
    }

    @Transactional
    public IntegrationBatchView reconcileBatch(long batchId) {
        ensureRuntimeRepository();
        IntegrationBatchView batch = runtimeRepository.getBatch(batchId);
        IntegrationAttemptView latest = runtimeRepository.latestAttempt(batchId);
        if (latest == null || latest.executionId() == null || latest.executionId().isBlank()) return batch;
        SeaTunnelGateway.JobStatus runtime = gateway.status(latest.executionId());
        String status = "NOT_FOUND".equalsIgnoreCase(runtime.status()) ? "UNKNOWN" : runtime.status();
        String message = "NOT_FOUND".equalsIgnoreCase(runtime.status())
                ? "SeaTunnel 无法确认该执行句柄，批次结果需要人工核对" : runtime.message();
        runtimeRepository.updateAttempt(latest.executionId(), status, message);
        return runtimeRepository.getBatch(batchId);
    }

    public List<IntegrationBatchView> batches(long taskId) {
        raw(taskId);
        ensureRuntimeRepository();
        return runtimeRepository.listBatches(taskId);
    }

    public List<IntegrationAttemptView> attempts(long batchId) {
        ensureRuntimeRepository();
        runtimeRepository.getBatch(batchId);
        return runtimeRepository.attempts(batchId);
    }

    public IntegrationCursorView cursor(long taskId) { raw(taskId); ensureRuntimeRepository(); return runtimeRepository.cursor(taskId); }

    public IntegrationCursorView saveCursor(long taskId, IntegrationRequests.CursorRequest request) {
        raw(taskId); ensureRuntimeRepository();
        return runtimeRepository.saveCursor(taskId, value(request.cursorColumn()), value(request.cursorValue()));
    }

    public void stop(String executionId) { gateway.cancel(executionId); }

    public List<IntegrationInstanceView> instances(long taskId) {
        return store.integrationInstances.values().stream().filter(item -> item.taskId() == taskId)
                .sorted(Comparator.comparing(IntegrationInstanceView::startedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<IntegrationTableView> tables(long taskId) {
        raw(taskId);
        List<IntegrationTableView> persisted = store.integrationTaskTables.getOrDefault(taskId, List.of());
        return persisted.isEmpty() ? legacyTable(raw(taskId)) : persisted;
    }

    @Transactional
    public IntegrationTaskView deleteTable(long taskId, long tableId) {
        IntegrationTaskView currentView = raw(taskId);
        ensureOffline(currentView);
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
                currentView.syncMode(), currentView.status(), currentView.lifecycleStatus(), secureEndpoint(remainingSource), secureEndpoint(remainingTarget),
                secureTransform(task.mappings(), task.options(), toRequests(remaining)), config, remaining);
        store.persistIntegrationTask(updated);
        store.integrationTasks.put(taskId, updated);
        store.integrationTaskTables.put(taskId, remaining);
        return masked(updated);
    }

    @Transactional
    public SeaTunnelGateway.JobStatus status(String executionId) {
        IntegrationInstanceView persisted = findInstance(executionId);
        SeaTunnelGateway.JobStatus status = gateway.status(executionId);
        if ("NOT_FOUND".equalsIgnoreCase(status.status()) && persisted != null) {
            if (terminal(persisted.status())) return new SeaTunnelGateway.JobStatus(executionId, persisted.status(), persisted.message());
            IntegrationInstanceView unknown = new IntegrationInstanceView(persisted.id(), persisted.taskId(), persisted.executionId(),
                    "UNKNOWN", persisted.startedAt(), LocalDateTime.now(), "SeaTunnel 无法确认执行句柄，结果需要 Reconcile 或人工核对");
            store.persistIntegrationInstance(unknown);
            store.integrationInstances.put(unknown.id(), unknown);
            if (runtimeRepository != null) runtimeRepository.updateAttempt(executionId, "UNKNOWN", unknown.message());
            return new SeaTunnelGateway.JobStatus(executionId, "UNKNOWN", unknown.message());
        }
        if (persisted != null) {
            IntegrationInstanceView updated = new IntegrationInstanceView(persisted.id(), persisted.taskId(), persisted.executionId(),
                    status.status(), persisted.startedAt(), terminal(status.status()) ? LocalDateTime.now() : null, status.message());
            store.persistIntegrationInstance(updated);
            store.integrationInstances.put(updated.id(), updated);
            if (runtimeRepository != null) runtimeRepository.updateAttempt(executionId, status.status(), status.message());
        }
        return status;
    }

    public String log(String executionId) {
        String live = gateway.log(executionId);
        if (live != null && !live.contains("没有该 SeaTunnel 执行日志") && !live.contains("实例不存在")) return live;
        IntegrationInstanceView persisted = findInstance(executionId);
        return persisted == null || persisted.message() == null ? live : persisted.message();
    }

    public void cancel(String executionId) { gateway.cancel(executionId); }

    private BatchExecution submitNewBatch(long taskId, String triggerType, String parametersJson, Long sourceBatchId,
                                          IntegrationTask runtimeTask, String config, Long clusterId) {
        IntegrationBatchView batch = runtimeRepository == null ? null
                : runtimeRepository.createBatch(taskId, triggerType, config, clusterId, parametersJson, sourceBatchId, "platform");
        try {
            SeaTunnelGateway.ValidationResult validation = gateway.validate(config, clusterId);
            if (!validation.valid()) throw new BadRequestException(validation.message());
            if (runtimeTask != null && schemaService != null) schemaService.prepare(runtimeTask);
            SeaTunnelGateway.SubmitResult result = gateway.submit(config, clusterId);
            persistLegacyInstance(taskId, result, clusterId);
            if (batch != null) runtimeRepository.recordAttempt(batch.id(), result.executionId(), result.status(), null);
            return new BatchExecution(batch, result);
        } catch (RuntimeException ex) {
            if (batch != null) runtimeRepository.recordAttempt(batch.id(), null, "FAILED", message(ex));
            throw ex;
        }
    }

    private SeaTunnelGateway.SubmitResult submitExistingBatch(IntegrationBatchView batch, String config) {
        try {
            SeaTunnelGateway.ValidationResult validation = gateway.validate(config, batch.clusterId());
            if (!validation.valid()) throw new BadRequestException(validation.message());
            SeaTunnelGateway.SubmitResult result = gateway.submit(config, batch.clusterId());
            persistLegacyInstance(batch.taskId(), result, batch.clusterId());
            runtimeRepository.recordAttempt(batch.id(), result.executionId(), result.status(), null);
            return result;
        } catch (RuntimeException ex) {
            runtimeRepository.recordAttempt(batch.id(), null, "FAILED", message(ex));
            throw ex;
        }
    }

    private void persistLegacyInstance(long taskId, SeaTunnelGateway.SubmitResult result, Long clusterId) {
        long instanceId = store.nextId();
        IntegrationInstanceView instance = new IntegrationInstanceView(instanceId, taskId, result.executionId(), result.status(),
                LocalDateTime.now(), terminal(result.status()) ? LocalDateTime.now() : null,
                clusterId == null ? "SeaTunnel 默认执行环境" : "SeaTunnel 执行环境 #" + clusterId);
        store.persistIntegrationInstance(instance);
        store.integrationInstances.put(instanceId, instance);
    }

    private void ensureRuntimeRepository() {
        if (runtimeRepository == null) throw new BadRequestException("离线同步批次运行库不可用");
    }

    private String normalizeTrigger(String value) {
        String trigger = value == null ? "MANUAL" : value.trim().toUpperCase();
        return Set.of("MANUAL", "WORKFLOW", "BACKFILL").contains(trigger) ? trigger : "MANUAL";
    }

    private boolean active(String status) {
        String value = status == null ? "" : status.toUpperCase();
        return Set.of("QUEUED", "SUBMITTED", "STARTING", "RUNNING").contains(value);
    }

    private String json(Map<String, Object> value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { return "{}"; }
    }

    private String value(String value) { return value == null ? "" : value.trim(); }
    private String message(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }

    private record BatchExecution(IntegrationBatchView batch, SeaTunnelGateway.SubmitResult result) { }

    private IntegrationInstanceView findInstance(String executionId) {
        return store.integrationInstances.values().stream().filter(item -> executionId.equals(item.executionId())).findFirst().orElse(null);
    }

    private IntegrationTaskView raw(long id) {
        IntegrationTaskView view = store.integrationTasks.get(id);
        if (view == null) throw new NotFoundException("同步任务不存在：" + id);
        return view;
    }

    private IntegrationTaskView masked(IntegrationTaskView view) {
        String masked = view.seatunnelConfig() == null ? null : view.seatunnelConfig()
                .replaceAll("(\"password\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?m)(password\\s*=\\s*\")[^\"]*(\")", "$1***$2");
        List<IntegrationTableView> tables = view.tables() == null || view.tables().isEmpty() ? legacyTable(view) : view.tables();
        return new IntegrationTaskView(view.id(), view.name(), view.sourceType(), view.targetType(), view.syncMode(), view.status(), view.lifecycleStatus(),
                maskJson(view.sourceConfigJson()), maskJson(view.targetConfigJson()), view.transformConfigJson(), masked, tables);
    }

    private IntegrationTask task(long id) {
        IntegrationTaskView view = raw(id);
        try {
            if (!hasStructuredConfig(view)) throw new BadRequestException("同步任务缺少结构化来源配置，请重新保存任务");
            var sourceStored = mapper.readValue(view.sourceConfigJson(), IntegrationRequests.Endpoint.class);
            var targetStored = mapper.readValue(view.targetConfigJson(), IntegrationRequests.Endpoint.class);
            var transform = mapper.readTree(view.transformConfigJson() == null ? "{}" : view.transformConfigJson());
            var mappings = mapper.convertValue(transform.path("mappings"), new TypeReference<List<IntegrationRequests.FieldMapping>>() { });
            var options = mapper.convertValue(transform.path("options"), new TypeReference<Map<String, Object>>() { });
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

    Long runtimeClusterId() {
        return store.seaTunnelClusters.values().stream()
                .sorted(Comparator.comparing((com.company.platform.cluster.SeaTunnelClusterView cluster) ->
                                !"HEALTHY".equalsIgnoreCase(cluster.healthStatus()))
                        .thenComparing(com.company.platform.cluster.SeaTunnelClusterView::createdAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(com.company.platform.cluster.SeaTunnelClusterView::id, Comparator.reverseOrder()))
                .map(com.company.platform.cluster.SeaTunnelClusterView::id)
                .findFirst().orElse(null);
    }

    private IntegrationTaskView updateLifecycle(IntegrationTaskView current, String lifecycleStatus) {
        IntegrationTaskView updated = new IntegrationTaskView(current.id(), current.name(), current.sourceType(), current.targetType(),
                current.syncMode(), current.status(), lifecycleStatus, current.sourceConfigJson(), current.targetConfigJson(),
                current.transformConfigJson(), current.seatunnelConfig(), current.tables());
        store.persistIntegrationTask(updated);
        store.integrationTasks.put(updated.id(), updated);
        return masked(updated);
    }

    private boolean isOnline(IntegrationTaskView task) {
        return "ONLINE".equalsIgnoreCase(task.lifecycleStatus());
    }

    private void ensureOffline(IntegrationTaskView task) {
        if (isOnline(task)) throw new BadRequestException("任务已上线，请先下线后再编辑");
    }

    private void validateMode(String syncMode, Map<String, Object> options) {
        String mode = normalizeMode(syncMode);
        if (!Set.of("FULL", "INCREMENTAL", "REALTIME").contains(mode)) {
            throw new BadRequestException("同步模式仅支持 FULL、INCREMENTAL、REALTIME");
        }
        if ("INCREMENTAL".equals(mode)) {
            String where = options == null || options.get("where") == null ? "" : String.valueOf(options.get("where")).trim();
            if (where.isBlank()) throw new BadRequestException("增量同步必须填写增量条件；留空请使用全量同步");
            String normalized = where.replaceAll("\\s+", " ").toLowerCase();
            if (!normalized.contains(">=") || !normalized.contains("<")) {
                throw new BadRequestException("离线增量必须使用 [start, end) 半开区间，例如 update_time >= :start AND update_time < :end，避免同时间戳漏数");
            }
        }
    }

    private Map<String, Object> effectiveOptions(Map<String, Object> requested, Long sourceDataSourceId, Long targetDataSourceId) {
        Map<String, Object> result = new HashMap<>(requested == null ? Map.of() : requested);
        if (sourceDataSourceId != null) result.put("sourceTimezone", dataSourceService.get(sourceDataSourceId).timezone());
        result.putIfAbsent("sourceTimezone", "Asia/Shanghai");
        if (targetDataSourceId != null) result.put("targetTimezone", dataSourceService.get(targetDataSourceId).timezone());
        result.putIfAbsent("targetTimezone", "Asia/Shanghai");
        return result;
    }

    private String normalizeMode(String value) {
        if (value == null) return "FULL";
        String mode = value.trim().toUpperCase();
        if (Set.of("CDC", "STREAMING", "REALTIME").contains(mode)) return "REALTIME";
        if (Set.of("INCREMENT", "INCREMENTAL").contains(mode)) return "INCREMENTAL";
        return mode.isBlank() ? "FULL" : mode;
    }

    private boolean terminal(String status) {
        if (status == null) return false;
        String value = status.toUpperCase();
        return Set.of("SUCCESS", "FINISHED", "FAILED", "CANCELLED", "KILLED", "LOST").contains(value);
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
    private IntegrationRequests.Endpoint resolveDataSource(Long dataSourceId, IntegrationRequests.Endpoint requested) {
        if (requested == null) throw new BadRequestException("同步任务缺少端点配置");
        if (dataSourceId == null) return requested;
        DataSourceService.ConnectionInfo stored = dataSourceService.connectionInfo(dataSourceId);
        String database = requested.database() == null || requested.database().isBlank() ? stored.databaseName() : requested.database();
        return new IntegrationRequests.Endpoint(dataSourceService.get(dataSourceId).host(), dataSourceService.get(dataSourceId).port(),
                database, stored.username(), stored.password(), requested.table());
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
            node.set("options", mapper.valueToTree(options == null ? Map.of() : options));
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
        if (current != null && current.tables() != null && !current.tables().isEmpty()) return current.tables();
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
        } catch (Exception ignored) { return List.of(); }
    }
}
