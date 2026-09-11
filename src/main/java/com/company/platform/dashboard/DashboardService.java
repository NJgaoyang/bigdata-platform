package com.company.platform.dashboard;

import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.DataSourceView;
import com.company.platform.development.DevFileView;
import com.company.platform.development.DevProjectView;
import com.company.platform.integration.IntegrationInstanceView;
import com.company.platform.integration.IntegrationTableView;
import com.company.platform.integration.IntegrationTaskView;
import com.company.platform.scheduler.SchedulerGateway;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read models built only from persisted/runtime facts; no sample task counts are synthesized. */
@Service
public class DashboardService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final String FAVORITES_PROJECT_NAME = "我的收藏";
    private final PlatformStore store;
    private final SchedulerGateway scheduler;

    public DashboardService(PlatformStore store, SchedulerGateway scheduler) { this.store = store; this.scheduler = scheduler; }

    public OverviewView overview() { return overview("admin"); }

    public OverviewView overview(String username) {
        SourceDashboardView sources = sources();
        List<RecentTask> recent = recentTasks();
        TaskSummary tasks = taskSummary(recent);
        boolean schedulerOnline = schedulerAvailable();
        return new OverviewView(new PlatformStatus(schedulerOnline ? "UP" : "DOWN", scheduler.isRealMode() ? "real" : "unavailable",
                sources.healthy(), sources.total(), schedulerOnline), tasks, sources, recent.stream().limit(12).toList(),
                favorites(username), trend(recent), generatedAt());
    }

    public SourceDashboardView sources() {
        List<DataSourceView> values = store.dataSources.values().stream()
                .sorted(Comparator.comparing(DataSourceView::name, String.CASE_INSENSITIVE_ORDER)).toList();
        int healthy = (int) values.stream().filter(this::healthy).count();
        int unhealthy = (int) values.stream().filter(this::unhealthy).count();
        Map<String, Long> types = values.stream().collect(Collectors.groupingBy(item -> item.type().name(), LinkedHashMap::new, Collectors.counting()));
        List<SourceItem> items = values.stream().map(source -> new SourceItem(source.id(), source.name(), source.type().name(),
                source.status(), source.host(), source.port(), source.databaseName(), source.username(), source.metadataVisible(),
                healthy(source), source.lastCheckedAt(), source.lastCheckMessage())).toList();
        return new SourceDashboardView(values.size(), healthy, unhealthy, values.size() - healthy - unhealthy,
                (int) values.stream().filter(DataSourceView::metadataVisible).count(), types, items);
    }

    public IntegrationDashboardView integration() { return integration("seven"); }

    public IntegrationDashboardView integration(String range) {
        List<IntegrationTaskView> tasks = store.integrationTasks.values().stream()
                .sorted(Comparator.comparing(IntegrationTaskView::name, String.CASE_INSENSITIVE_ORDER)).toList();
        List<IntegrationInstanceView> instances = integrationInstances(range);
        TaskSummary summary = integrationSummary(instances);
        Map<String, Long> bySource = tasks.stream().collect(Collectors.groupingBy(IntegrationTaskView::sourceType,
                LinkedHashMap::new, Collectors.counting()));
        return new IntegrationDashboardView(tasks.size(), summary.total(), summary.running(), summary.success(), summary.failed(),
                summary.pending(), bySource, integrationTrend(instances, range), generatedAt());
    }

    public OperationsDashboardView operations() {
        List<RecentTask> recent = schedulerRecentTasks();
        TaskSummary summary = taskSummary(recent);
        List<AlertItem> alerts = recent.stream().filter(item -> isFailed(item.status()))
                .map(item -> new AlertItem(item.id(), item.name(), item.type(), item.startedAt(), item.status())).toList();
        return new OperationsDashboardView(summary, schedulerAvailable(), scheduler.isRealMode() ? "real" : "unavailable",
                recent.stream().limit(12).toList(), alerts.stream().limit(12).toList(), trend(recent), generatedAt());
    }

    public AssetDashboardView assets() {
        Map<String, AssetItem> items = new LinkedHashMap<>();
        Set<Long> successfulTaskIds = store.integrationInstances.values().stream()
                .filter(item -> isSuccess(item.status())).map(IntegrationInstanceView::taskId).collect(Collectors.toSet());
        for (Map.Entry<Long, List<IntegrationTableView>> entry : store.integrationTaskTables.entrySet()) {
            boolean taskSucceeded = successfulTaskIds.contains(entry.getKey());
            for (IntegrationTableView table : entry.getValue()) {
                putAsset(items, table.sourceDatabase(), table.sourceTable(), "SOURCE", "已登记");
                putAsset(items, table.targetDatabase(), table.targetTable(), "TARGET", taskSucceeded ? "已同步" : "待同步");
            }
        }
        // A parsed lineage relation only proves that a dependency was discovered. It does
        // not prove that a governance rule, quality policy or stewardship workflow ran.
        // Keep lineage and synchronization facts separate from governance facts until a
        // real governance execution model is persisted by the platform.
        store.lineages.values().forEach(lineage -> {
            putQualifiedAsset(items, lineage.sourceTable(), "LINEAGE_SOURCE", "已关联");
            putQualifiedAsset(items, lineage.targetTable(), "LINEAGE_TARGET", "已关联");
        });
        List<AssetItem> assets = items.values().stream().sorted(Comparator.comparing(AssetItem::name, String.CASE_INSENSITIVE_ORDER)).toList();
        return new AssetDashboardView(assets.size(), assets.size(), store.dataSources.values().stream().filter(DataSourceView::metadataVisible).count(),
                0, store.lineages.size(), assets, generatedAt());
    }

    private List<FavoriteItem> favorites(String username) {
        String owner = username == null || username.isBlank() ? "admin" : username.trim();
        Set<Long> favoriteProjectIds = store.projects.values().stream()
                .filter(project -> FAVORITES_PROJECT_NAME.equalsIgnoreCase(project.name() == null ? "" : project.name().trim()))
                .filter(project -> owner.equalsIgnoreCase(project.ownerName() == null ? "" : project.ownerName().trim()))
                .map(DevProjectView::id)
                .collect(Collectors.toSet());
        if (favoriteProjectIds.isEmpty()) return List.of();
        return store.files.values().stream()
                .filter(file -> favoriteProjectIds.contains(file.projectId()))
                .sorted(Comparator.comparing(DevFileView::name, String.CASE_INSENSITIVE_ORDER))
                .map(file -> new FavoriteItem("development-file-" + file.id(), file.name(),
                        file.fileType() == null || file.fileType().isBlank() ? "FILE" : file.fileType(),
                        file.description() == null || file.description().isBlank() ? "开发文件" : file.description()))
                .toList();
    }

    private String putAsset(Map<String, AssetItem> items, String database, String table, String origin, String status) {
        String db = database == null ? "" : database.trim();
        String name = table == null ? "" : table.trim();
        String key = db.isBlank() ? name : db + "." + name;
        if (!name.isBlank()) items.putIfAbsent(key, new AssetItem(key, db, name, "TABLE", layer(db), status, origin));
        return key;
    }

    private String putQualifiedAsset(Map<String, AssetItem> items, String qualifiedName, String origin, String status) {
        String value = qualifiedName == null ? "" : qualifiedName.trim();
        int separator = value.indexOf('.');
        return separator > 0 ? putAsset(items, value.substring(0, separator), value.substring(separator + 1), origin, status)
                : putAsset(items, "", value, origin, status);
    }

    private String layer(String database) {
        String value = database == null ? "" : database.toLowerCase(Locale.ROOT);
        return Set.of("ods", "dwd", "dws", "ads", "dim").contains(value) ? value.toUpperCase(Locale.ROOT) : "SOURCE";
    }

    private TaskSummary taskSummary(List<RecentTask> tasks) {
        int running = (int) tasks.stream().filter(item -> isRunning(item.status())).count();
        int success = (int) tasks.stream().filter(item -> isSuccess(item.status())).count();
        int failed = (int) tasks.stream().filter(item -> isFailed(item.status())).count();
        return new TaskSummary(tasks.size(), running, success, failed, Math.max(0, tasks.size() - running - success - failed));
    }

    private TaskSummary integrationSummary(List<IntegrationInstanceView> instances) {
        int running = (int) instances.stream().filter(item -> isRunning(item.status())).count();
        int success = (int) instances.stream().filter(item -> isSuccess(item.status())).count();
        int failed = (int) instances.stream().filter(item -> isFailed(item.status())).count();
        return new TaskSummary(instances.size(), running, success, failed, Math.max(0, instances.size() - running - success - failed));
    }

    private List<RecentTask> recentTasks() {
        Map<Long, IntegrationTaskView> integrations = store.integrationTasks;
        List<RecentTask> result = new ArrayList<>();
        for (IntegrationInstanceView instance : store.integrationInstances.values()) {
            IntegrationTaskView task = integrations.get(instance.taskId());
            result.add(new RecentTask("integration-" + instance.id(), task == null ? "未命名同步任务" : task.name(), "数据集成",
                    instance.status(), instance.startedAt(), instance.finishedAt(), instance.message()));
        }
        // Workflow definitions are configuration, not executions. Only real scheduler
        // instances belong in execution KPIs and recent-task timelines.
        for (Map<String, Object> item : schedulerInstances()) {
            result.add(new RecentTask("scheduler-" + item.getOrDefault("id", item.getOrDefault("processInstanceId", item.hashCode())),
                    String.valueOf(item.getOrDefault("name", "调度实例")), "调度实例", String.valueOf(item.getOrDefault("status", "UNKNOWN")),
                    asDateTime(item.get("startTime")), asDateTime(item.get("endTime")), String.valueOf(item.getOrDefault("duration", ""))));
        }
        return result.stream().sorted(Comparator.comparing(RecentTask::startedAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    private List<RecentTask> schedulerRecentTasks() {
        List<RecentTask> result = new ArrayList<>();
        for (Map<String, Object> item : schedulerInstances()) {
            result.add(new RecentTask("scheduler-" + item.getOrDefault("id", item.getOrDefault("processInstanceId", item.hashCode())),
                    String.valueOf(item.getOrDefault("name", "调度实例")), "调度实例", String.valueOf(item.getOrDefault("status", "UNKNOWN")),
                    asDateTime(item.get("startTime")), asDateTime(item.get("endTime")), String.valueOf(item.getOrDefault("duration", ""))));
        }
        return result.stream().sorted(Comparator.comparing(RecentTask::startedAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    private List<IntegrationInstanceView> integrationInstances(String range) {
        String value = range == null ? "seven" : range.trim().toLowerCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = "hours".equals(value) ? now.minusHours(24)
                : "thirty".equals(value) ? now.minusDays(29).toLocalDate().atStartOfDay()
                : now.minusDays(6).toLocalDate().atStartOfDay();
        return store.integrationInstances.values().stream().filter(item -> item.startedAt() != null)
                .filter(item -> !item.startedAt().isBefore(start) && !item.startedAt().isAfter(now)).toList();
    }

    private List<TrendPoint> integrationTrend(List<IntegrationInstanceView> instances, String range) {
        String value = range == null ? "seven" : range.trim().toLowerCase(Locale.ROOT);
        if ("hours".equals(value)) {
            LocalDateTime end = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1);
            Map<LocalDateTime, TrendCounter> counts = new LinkedHashMap<>();
            for (int offset = 7; offset >= 0; offset--) counts.put(end.minusHours(offset * 3L), new TrendCounter());
            for (IntegrationInstanceView instance : instances) {
                if (instance.startedAt() == null) continue;
                for (Map.Entry<LocalDateTime, TrendCounter> item : counts.entrySet()) {
                    LocalDateTime bucketEnd = item.getKey();
                    if (!instance.startedAt().isBefore(bucketEnd.minusHours(3)) && instance.startedAt().isBefore(bucketEnd)) {
                        increase(item.getValue(), instance.status());
                        break;
                    }
                }
            }
            return counts.entrySet().stream().map(item -> new TrendPoint(HOUR.format(item.getKey()), item.getValue().count,
                    item.getValue().success, item.getValue().failed, item.getValue().running)).toList();
        }
        int days = "thirty".equals(value) ? 30 : 7;
        Map<LocalDate, TrendCounter> counts = new LinkedHashMap<>();
        for (int offset = days - 1; offset >= 0; offset--) counts.put(LocalDate.now().minusDays(offset), new TrendCounter());
        for (IntegrationInstanceView instance : instances) {
            if (instance.startedAt() != null && counts.containsKey(instance.startedAt().toLocalDate())) increase(counts.get(instance.startedAt().toLocalDate()), instance.status());
        }
        return counts.entrySet().stream().map(item -> new TrendPoint(DAY.format(item.getKey()), item.getValue().count,
                item.getValue().success, item.getValue().failed, item.getValue().running)).toList();
    }

    private void increase(TrendCounter counter, String status) {
        counter.count++;
        if (isSuccess(status)) counter.success++;
        else if (isFailed(status)) counter.failed++;
        else if (isRunning(status)) counter.running++;
    }

    private List<TrendPoint> trend(List<RecentTask> tasks) {
        Map<LocalDate, TrendCounter> counts = new LinkedHashMap<>();
        for (int offset = 6; offset >= 0; offset--) counts.put(LocalDate.now().minusDays(offset), new TrendCounter());
        for (RecentTask task : tasks) {
            if (task.startedAt() == null || !counts.containsKey(task.startedAt().toLocalDate())) continue;
            TrendCounter counter = counts.get(task.startedAt().toLocalDate());
            counter.count++;
            if (isSuccess(task.status())) counter.success++;
            else if (isFailed(task.status())) counter.failed++;
            else if (isRunning(task.status())) counter.running++;
        }
        return counts.entrySet().stream().map(item -> new TrendPoint(DAY.format(item.getKey()), item.getValue().count,
                item.getValue().success, item.getValue().failed, item.getValue().running)).toList();
    }

    private boolean schedulerAvailable() {
        try { scheduler.listProcessInstances(); return true; }
        catch (RuntimeException ignored) { return false; }
    }
    private List<Map<String, Object>> schedulerInstances() {
        try { return scheduler.listProcessInstances(); }
        catch (RuntimeException ignored) { return List.of(); }
    }
    private boolean healthy(DataSourceView source) { return "ACTIVE".equals(source.status() == null ? "" : source.status().toUpperCase(Locale.ROOT)); }
    private boolean unhealthy(DataSourceView source) {
        String status = source.status() == null ? "" : source.status().toUpperCase(Locale.ROOT);
        return status.contains("DOWN") || status.contains("FAIL") || status.contains("ERROR") || status.contains("UNAVAILABLE");
    }
    private boolean isRunning(String status) { String value = normalize(status); return value.contains("RUNNING") || value.contains("SUBMITTED") || value.contains("运行中"); }
    private boolean isSuccess(String status) { String value = normalize(status); return value.contains("SUCCESS") || value.contains("FINISHED") || value.contains("成功"); }
    private boolean isFailed(String status) { String value = normalize(status); return value.contains("FAIL") || value.contains("ERROR") || value.contains("失败"); }
    private String normalize(String status) { return status == null ? "" : status.trim().toUpperCase(Locale.ROOT); }
    private LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime date) return date;
        if (value == null) return null;
        try { return LocalDateTime.parse(String.valueOf(value).replace(" ", "T").substring(0, 19)); }
        catch (RuntimeException ignored) { return null; }
    }
    private LocalDateTime generatedAt() { return LocalDateTime.now(); }

    public record PlatformStatus(String status, String mode, int onlineDataSources, int totalDataSources, boolean schedulerAvailable) { }
    public record TaskSummary(int total, int running, int success, int failed, int pending) { }
    public record TrendPoint(String date, long count, long success, long failed, long running) { }
    public record RecentTask(String id, String name, String type, String status, LocalDateTime startedAt, LocalDateTime finishedAt, String detail) { }
    public record FavoriteItem(String id, String name, String type, String detail) { }
    public record SourceItem(long id, String name, String type, String status, String host, int port, String databaseName,
                             String username, boolean metadataVisible, boolean healthy, LocalDateTime lastCheckedAt, String lastCheckMessage) { }
    public record AlertItem(String id, String name, String type, LocalDateTime occurredAt, String status) { }
    public record AssetItem(String name, String database, String table, String type, String layer, String status, String origin) { }
    public record OverviewView(PlatformStatus platform, TaskSummary tasks, SourceDashboardView sources, List<RecentTask> recentTasks, List<FavoriteItem> favorites, List<TrendPoint> trend, LocalDateTime generatedAt) { }
    public record SourceDashboardView(int total, int healthy, int unhealthy, int unchecked, int metadataVisible,
                                      Map<String, Long> typeDistribution, List<SourceItem> items) { }
    public record IntegrationDashboardView(int taskTotal, int total, int running, int success, int failed, int pending,
                                           Map<String, Long> sourceDistribution, List<TrendPoint> trend, LocalDateTime generatedAt) { }
    public record OperationsDashboardView(TaskSummary tasks, boolean schedulerAvailable, String mode, List<RecentTask> recentTasks, List<AlertItem> alerts, List<TrendPoint> trend, LocalDateTime generatedAt) { }
    public record AssetDashboardView(long total, long tables, long visibleCatalogs, long governed, long lineageRelations,
                                     List<AssetItem> items, LocalDateTime generatedAt) { }

    private static final class TrendCounter {
        private long count;
        private long success;
        private long failed;
        private long running;
    }
}
