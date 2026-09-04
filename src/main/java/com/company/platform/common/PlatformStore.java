package com.company.platform.common;

import com.company.platform.datasource.DataSourceView;
import com.company.platform.cluster.SeaTunnelClusterView;
import com.company.platform.development.DevFileView;
import com.company.platform.development.DevFolderView;
import com.company.platform.development.DevProjectView;
import com.company.platform.development.FileVersionView;
import com.company.platform.integration.IntegrationTaskView;
import com.company.platform.lineage.LineageView;
import com.company.platform.scheduler.ScheduleConfigView;
import com.company.platform.system.AlertChannelView;
import com.company.platform.system.AuditLogView;
import com.company.platform.system.RoleView;
import com.company.platform.system.UserView;
import com.company.platform.integration.IntegrationInstanceView;
import com.company.platform.integration.IntegrationTableView;
import com.company.platform.query.QueryHistoryView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.event.EventListener;
import com.company.platform.workflow.WorkflowView;
import com.company.platform.workflow.WorkflowNodeView;
import com.company.platform.workflow.WorkflowEdgeView;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PlatformStore {
    private final AtomicLong ids = new AtomicLong(1000);
    private JdbcTemplate jdbc;
    public final Map<Long, DataSourceView> dataSources = new ConcurrentHashMap<>();
    public final Map<Long, String> encryptedDataSourcePasswords = new ConcurrentHashMap<>();
    public final Map<Long, SeaTunnelClusterView> seaTunnelClusters = new ConcurrentHashMap<>();
    public final Map<Long, String> encryptedClusterPasswords = new ConcurrentHashMap<>();
    public final Map<Long, DevProjectView> projects = new ConcurrentHashMap<>();
    public final Map<Long, DevFolderView> folders = new ConcurrentHashMap<>();
    public final Map<Long, DevFileView> files = new ConcurrentHashMap<>();
    public final Map<Long, FileVersionView> versions = new ConcurrentHashMap<>();
    public final Map<Long, IntegrationTaskView> integrationTasks = new ConcurrentHashMap<>();
    public final Map<Long, List<IntegrationTableView>> integrationTaskTables = new ConcurrentHashMap<>();
    public final Map<Long, WorkflowView> workflows = new ConcurrentHashMap<>();
    public final Map<Long, LineageView> lineages = new ConcurrentHashMap<>();
    public final Map<Long, ScheduleConfigView> scheduleConfigs = new ConcurrentHashMap<>();
    public final Map<Long, UserView> users = new ConcurrentHashMap<>();
    public final Map<Long, RoleView> roles = new ConcurrentHashMap<>();
    public final Map<Long, Set<String>> userPermissions = new ConcurrentHashMap<>();
    public final Map<Long, AlertChannelView> alertChannels = new ConcurrentHashMap<>();
    public final Map<Long, AuditLogView> auditLogs = new ConcurrentHashMap<>();
    public final Map<Long, IntegrationInstanceView> integrationInstances = new ConcurrentHashMap<>();
    public final Map<String, String> projectPermissions = new ConcurrentHashMap<>();
    public final Map<String, String> datasourcePermissions = new ConcurrentHashMap<>();
    public final Map<Long, String> operationLogs = new ConcurrentHashMap<>();

    public long nextId() { return ids.incrementAndGet(); }

    @Autowired(required = false)
    public void setJdbcTemplate(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @EventListener(ApplicationReadyEvent.class)
    public void loadPersistedCoreData() {
        if (jdbc == null) return;
        try {
            jdbc.query("SELECT id,name,type,host,port,database_name,username,password_ciphertext,status FROM data_source", rs -> {
                long id = rs.getLong("id");
                dataSources.put(id, new DataSourceView(id, rs.getString("name"),
                        com.company.platform.datasource.DataSourceType.valueOf(rs.getString("type")),
                        rs.getString("host"), rs.getInt("port"), rs.getString("database_name"),
                        rs.getString("username"), rs.getString("status")));
                String encrypted = rs.getString("password_ciphertext");
                if (encrypted != null) encryptedDataSourcePasswords.put(id, encrypted);
                advanceId(id);
            });
            jdbc.query("SELECT id,name,description,status FROM dev_project", rs -> {
                long id = rs.getLong("id");
                projects.put(id, new DevProjectView(id, rs.getString("name"), rs.getString("description"), rs.getString("status")));
                advanceId(id);
            });
            jdbc.query("SELECT id,project_id,parent_id,name FROM dev_folder", rs -> {
                long id = rs.getLong("id");
                Long parent = rs.getObject("parent_id", Long.class);
                folders.put(id, new DevFolderView(id, rs.getLong("project_id"), parent, rs.getString("name")));
                advanceId(id);
            });
            jdbc.query("SELECT id,project_id,folder_id,name,file_type,content,status,current_version FROM dev_file", rs -> {
                long id = rs.getLong("id");
                Long folder = rs.getObject("folder_id", Long.class);
                files.put(id, new DevFileView(id, rs.getLong("project_id"), folder, rs.getString("name"),
                        rs.getString("file_type"), rs.getString("content"), rs.getString("status"), rs.getInt("current_version")));
                advanceId(id);
            });
            jdbc.query("SELECT id,file_id,version_no,content,checksum,publish_flag FROM dev_file_version", rs -> {
                long id = rs.getLong("id");
                versions.put(id, new FileVersionView(id, rs.getLong("file_id"), rs.getInt("version_no"),
                        rs.getString("content"), rs.getString("checksum"), rs.getBoolean("publish_flag")));
                advanceId(id);
            });
            jdbc.query("SELECT id,task_id,source_database,source_table,target_database,target_table,partition_column FROM integration_task_table", rs -> {
                long taskId = rs.getLong("task_id");
                integrationTaskTables.computeIfAbsent(taskId, ignored -> new ArrayList<>()).add(new IntegrationTableView(
                        rs.getLong("id"), taskId, rs.getString("source_database"), rs.getString("source_table"),
                        rs.getString("target_database"), rs.getString("target_table"), rs.getString("partition_column")));
                advanceId(rs.getLong("id"));
            });
            jdbc.query("SELECT id,name,source_type,target_type,sync_mode,status,source_config_json,target_config_json,transform_config_json,seatunnel_config FROM integration_task", rs -> {
                long id = rs.getLong("id");
                integrationTasks.put(id, new IntegrationTaskView(id, rs.getString("name"), rs.getString("source_type"),
                        rs.getString("target_type"), rs.getString("sync_mode"), rs.getString("status"),
                        rs.getString("source_config_json"), rs.getString("target_config_json"), rs.getString("transform_config_json"), rs.getString("seatunnel_config"),
                        integrationTaskTables.getOrDefault(id, List.of())));
                advanceId(id);
            });
            jdbc.query("SELECT id,task_id,execution_id,status,started_at,finished_at,error_message FROM integration_instance", rs -> {
                long id = rs.getLong("id");
                var started = rs.getTimestamp("started_at");
                var finished = rs.getTimestamp("finished_at");
                integrationInstances.put(id, new IntegrationInstanceView(id, rs.getLong("task_id"), rs.getString("execution_id"),
                        rs.getString("status"), started == null ? null : started.toLocalDateTime(),
                        finished == null ? null : finished.toLocalDateTime(), rs.getString("error_message")));
                advanceId(id);
            });
            Map<Long, List<WorkflowNodeView>> loadedNodes = new HashMap<>();
            Map<Long, List<WorkflowEdgeView>> loadedEdges = new HashMap<>();
            jdbc.query("SELECT id,workflow_id,node_code,node_name,node_type,file_version_id,config_json,x,y FROM workflow_node", rs -> {
                long workflowId = rs.getLong("workflow_id");
                loadedNodes.computeIfAbsent(workflowId, ignored -> new ArrayList<>()).add(new WorkflowNodeView(
                        rs.getLong("id"), rs.getString("node_name"), com.company.platform.workflow.NodeType.valueOf(rs.getString("node_type")),
                        rs.getObject("file_version_id", Long.class), rs.getString("config_json"), rs.getInt("x"), rs.getInt("y"), rs.getString("node_code")));
                advanceId(rs.getLong("id"));
            });
            jdbc.query("SELECT id,workflow_id,source_node_id,target_node_id FROM workflow_edge", rs -> {
                long workflowId = rs.getLong("workflow_id");
                loadedEdges.computeIfAbsent(workflowId, ignored -> new ArrayList<>()).add(new WorkflowEdgeView(
                        rs.getLong("id"), rs.getLong("source_node_id"), rs.getLong("target_node_id")));
                advanceId(rs.getLong("id"));
            });
            jdbc.query("SELECT id,name,workflow_code,description,status,ds_process_code,published_version FROM workflow", rs -> {
                long id = rs.getLong("id");
                Integer version = rs.getObject("published_version", Integer.class);
                workflows.put(id, new WorkflowView(id, rs.getString("name"), rs.getString("workflow_code"), rs.getString("description"),
                        rs.getString("status"), version == null ? 0 : version, loadedNodes.getOrDefault(id, List.of()),
                        loadedEdges.getOrDefault(id, List.of()), rs.getString("ds_process_code")));
                advanceId(id);
            });
            jdbc.query("SELECT id,username,display_name,role_code,status,created_at,password_hash FROM platform_user", rs -> {
                long id = rs.getLong("id");
                var createdAt = rs.getTimestamp("created_at");
                users.put(id, new UserView(id, rs.getString("username"), rs.getString("display_name"),
                        rs.getString("role_code"), rs.getString("status"),
                        createdAt == null ? LocalDateTime.now() : createdAt.toLocalDateTime(), rs.getString("password_hash")));
                advanceId(id);
            });
            jdbc.query("SELECT id,name,host,port,ssh_username,ssh_port,ssh_password_encrypted,seatunnel_home,description,health_status,created_at FROM seatunnel_cluster", rs -> {
                long id = rs.getLong("id");
                var createdAt = rs.getTimestamp("created_at");
                seaTunnelClusters.put(id, new SeaTunnelClusterView(id, rs.getString("name"), rs.getString("host"), rs.getInt("port"),
                        rs.getString("ssh_username"), rs.getInt("ssh_port"), rs.getString("seatunnel_home"), rs.getString("description"),
                        rs.getString("health_status"), createdAt == null ? LocalDateTime.now() : createdAt.toLocalDateTime()));
                encryptedClusterPasswords.put(id, rs.getString("ssh_password_encrypted"));
                advanceId(id);
            });
            jdbc.query("SELECT user_id,permission_code FROM user_permission", (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                    userPermissions.computeIfAbsent(rs.getLong("user_id"), ignored -> ConcurrentHashMap.newKeySet()).add(rs.getString("permission_code")));
            jdbc.query("SELECT id,role_code,role_name FROM platform_role", rs -> {
                long id = rs.getLong("id");
                roles.put(id, new RoleView(id, rs.getString("role_code"), rs.getString("role_name"), new HashSet<>()));
                advanceId(id);
            });
            jdbc.query("SELECT role_id,permission_code FROM role_permission", rs -> {
                long roleId = rs.getLong("role_id");
                RoleView role = roles.get(roleId);
                if (role != null) {
                    HashSet<String> permissions = new HashSet<>(role.permissions());
                    permissions.add(rs.getString("permission_code"));
                    roles.put(roleId, new RoleView(role.id(), role.roleCode(), role.roleName(), permissions));
                }
            });
            jdbc.query("SELECT project_id,user_id,permission_code FROM project_member", rs -> {
                String key = rs.getLong("project_id") + ":" + rs.getLong("user_id") + ":" + rs.getString("permission_code");
                projectPermissions.put(key, rs.getString("permission_code"));
            });
            jdbc.query("SELECT datasource_id,user_id,permission_code FROM datasource_permission", rs -> {
                String key = rs.getLong("datasource_id") + ":" + rs.getLong("user_id") + ":" + rs.getString("permission_code");
                datasourcePermissions.put(key, rs.getString("permission_code"));
            });
            jdbc.query("SELECT id,name,channel_type,config_json,enabled FROM alert_channel", rs -> {
                long id = rs.getLong("id");
                alertChannels.put(id, new AlertChannelView(id, rs.getString("name"), rs.getString("channel_type"), rs.getString("config_json"), rs.getBoolean("enabled")));
                advanceId(id);
            });
            jdbc.query("SELECT id,action,resource_type,resource_id,detail,operator_name,created_at FROM operation_audit", rs -> {
                long id = rs.getLong("id");
                var timestamp = rs.getTimestamp("created_at");
                auditLogs.put(id, new AuditLogView(id, rs.getString("action"), rs.getString("resource_type"),
                        rs.getObject("resource_id", Long.class), rs.getString("detail"), rs.getString("operator_name"),
                        timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime()));
                advanceId(id);
            });
            jdbc.query("SELECT id,workflow_id,cron_expression,timezone,enabled,failure_strategy,worker_group,alert_group,ds_schedule_id,parallelism FROM schedule_config", rs -> {
                long id = rs.getLong("id");
                scheduleConfigs.put(id, new ScheduleConfigView(id, rs.getLong("workflow_id"), rs.getString("cron_expression"),
                        rs.getString("timezone"), rs.getBoolean("enabled"), rs.getString("failure_strategy"), rs.getInt("parallelism"),
                        rs.getString("worker_group"), rs.getString("alert_group"), rs.getString("ds_schedule_id")));
                advanceId(id);
            });
            jdbc.query("SELECT id,source_table_name,target_table_name,relation_type,dev_file_id,file_version_id FROM data_lineage", rs -> {
                long id = rs.getLong("id");
                lineages.put(id, new LineageView(id, rs.getString("source_table_name"), rs.getString("target_table_name"),
                        rs.getString("relation_type"), rs.getObject("dev_file_id", Long.class), rs.getObject("file_version_id", Long.class)));
                advanceId(id);
            });
        } catch (RuntimeException ignored) {
            // A clean development database may not be reachable during startup; memory mode remains usable.
        } finally {
            refreshIdSequence();
        }
    }

    public void persistDataSource(DataSourceView view, String encryptedPassword) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE data_source SET name=?,type=?,host=?,port=?,database_name=?,username=?,password_ciphertext=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                view.name(), view.type().name(), view.host(), view.port(), view.databaseName(), view.username(), encryptedPassword, view.status(), view.id());
        if (updated == 0) jdbc.update("INSERT INTO data_source (id,name,type,host,port,database_name,username,password_ciphertext,status) VALUES (?,?,?,?,?,?,?,?,?)",
                view.id(), view.name(), view.type().name(), view.host(), view.port(), view.databaseName(), view.username(), encryptedPassword, view.status());
    }
    public void persistProject(DevProjectView view) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE dev_project SET name=?,description=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", view.name(), view.description(), view.status(), view.id());
        if (updated == 0) jdbc.update("INSERT INTO dev_project (id,name,description,status) VALUES (?,?,?,?)", view.id(), view.name(), view.description(), view.status());
    }
    public void persistFolder(DevFolderView view) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE dev_folder SET project_id=?,parent_id=?,name=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", view.projectId(), view.parentId(), view.name(), view.id());
        if (updated == 0) jdbc.update("INSERT INTO dev_folder (id,project_id,parent_id,name) VALUES (?,?,?,?)", view.id(), view.projectId(), view.parentId(), view.name());
    }
    public void persistFile(DevFileView view) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE dev_file SET project_id=?,folder_id=?,name=?,file_type=?,content=?,status=?,current_version=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                view.projectId(), view.folderId(), view.name(), view.fileType(), view.content(), view.status(), view.currentVersion(), view.id());
        if (updated == 0) jdbc.update("INSERT INTO dev_file (id,project_id,folder_id,name,file_type,content,status,current_version) VALUES (?,?,?,?,?,?,?,?)",
                view.id(), view.projectId(), view.folderId(), view.name(), view.fileType(), view.content(), view.status(), view.currentVersion());
    }
    public void persistVersion(FileVersionView view) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE dev_file_version SET file_id=?,version_no=?,content=?,checksum=?,publish_flag=? WHERE id=?",
                view.fileId(), view.versionNo(), view.content(), view.checksum(), view.publishFlag(), view.id());
        if (updated == 0) jdbc.update("INSERT INTO dev_file_version (id,file_id,version_no,content,checksum,publish_flag) VALUES (?,?,?,?,?,?)",
                view.id(), view.fileId(), view.versionNo(), view.content(), view.checksum(), view.publishFlag());
    }
    public void deleteCore(String table, long id) {
        if (jdbc == null || !Set.of("data_source", "dev_project", "dev_folder", "dev_file").contains(table)) return;
        jdbc.update("DELETE FROM " + table + " WHERE id = ?", id);
    }
    public void deleteFileData(long fileId) {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM data_lineage WHERE dev_file_id=?", fileId);
        jdbc.update("DELETE FROM dev_file_version WHERE file_id=?", fileId);
        jdbc.update("DELETE FROM dev_file WHERE id=?", fileId);
    }
    public void persistLineage(LineageView lineage) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE data_lineage SET source_table_name=?,target_table_name=?,relation_type=?,dev_file_id=?,file_version_id=? WHERE id=?",
                lineage.sourceTable(), lineage.targetTable(), lineage.relationType(), lineage.fileId(), lineage.fileVersionId(), lineage.id());
        if (updated == 0) jdbc.update("INSERT INTO data_lineage (id,source_table_name,target_table_name,relation_type,dev_file_id,file_version_id) VALUES (?,?,?,?,?,?)",
                lineage.id(), lineage.sourceTable(), lineage.targetTable(), lineage.relationType(), lineage.fileId(), lineage.fileVersionId());
    }
    public void deleteLineageForFile(long fileId) {
        if (jdbc != null) jdbc.update("DELETE FROM data_lineage WHERE dev_file_id=?", fileId);
    }
    public void deleteWorkflow(long id) {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM workflow_node WHERE workflow_id=?", id);
        jdbc.update("DELETE FROM workflow_edge WHERE workflow_id=?", id);
        jdbc.update("DELETE FROM schedule_config WHERE workflow_id=?", id);
        jdbc.update("DELETE FROM workflow WHERE id=?", id);
    }
    public void persistIntegrationTask(IntegrationTaskView task) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE integration_task SET name=?,source_type=?,target_type=?,sync_mode=?,status=?,source_config_json=?,target_config_json=?,transform_config_json=?,seatunnel_config=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                task.name(), task.sourceType(), task.targetType(), task.syncMode(), task.status(), task.sourceConfigJson(), task.targetConfigJson(), task.transformConfigJson(), task.seatunnelConfig(), task.id());
        if (updated == 0) jdbc.update("INSERT INTO integration_task (id,name,source_type,target_type,source_config_json,target_config_json,transform_config_json,sync_mode,status,seatunnel_config) VALUES (?,?,?,?,?,?,?,?,?,?)",
                task.id(), task.name(), task.sourceType(), task.targetType(), task.sourceConfigJson(), task.targetConfigJson(), task.transformConfigJson(), task.syncMode(), task.status(), task.seatunnelConfig());
        jdbc.update("DELETE FROM integration_task_table WHERE task_id=?", task.id());
        for (IntegrationTableView table : task.tables()) {
            jdbc.update("INSERT INTO integration_task_table (id,task_id,source_database,source_table,target_database,target_table,partition_column) VALUES (?,?,?,?,?,?,?)",
                    table.id(), task.id(), table.sourceDatabase(), table.sourceTable(), table.targetDatabase(), table.targetTable(), table.partitionColumn());
        }
        integrationTaskTables.put(task.id(), task.tables());
    }
    public void deleteIntegrationTask(long id) {
        integrationTaskTables.remove(id);
        if (jdbc == null) return;
        jdbc.update("DELETE FROM integration_instance WHERE task_id=?", id);
        jdbc.update("DELETE FROM integration_task_table WHERE task_id=?", id);
        jdbc.update("DELETE FROM integration_task WHERE id=?", id);
    }
    public void persistIntegrationInstance(IntegrationInstanceView instance) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE integration_instance SET task_id=?,execution_id=?,status=?,started_at=?,finished_at=?,error_message=? WHERE id=?",
                instance.taskId(), instance.executionId(), instance.status(), instance.startedAt(), instance.finishedAt(), instance.message(), instance.id());
        if (updated == 0) jdbc.update("INSERT INTO integration_instance (id,task_id,execution_id,status,started_at,finished_at,error_message) VALUES (?,?,?,?,?,?,?)",
                instance.id(), instance.taskId(), instance.executionId(), instance.status(), instance.startedAt(), instance.finishedAt(), instance.message());
    }
    public void persistAudit(AuditLogView log) {
        if (jdbc == null) return;
        jdbc.update("INSERT INTO operation_audit (id,action,resource_type,resource_id,detail,operator_name,created_at) VALUES (?,?,?,?,?,?,?)",
                log.id(), log.action(), log.resourceType(), log.resourceId(), log.detail(), log.operatorName(), log.createdAt());
    }
    public void persistUser(UserView user) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE platform_user SET username=?,display_name=?,role_code=?,status=?,password_hash=? WHERE id=?",
                user.username(), user.displayName(), user.roleCode(), user.status(), user.passwordHash(), user.id());
        if (updated == 0) jdbc.update("INSERT INTO platform_user (id,username,display_name,role_code,status,created_at,password_hash) VALUES (?,?,?,?,?,?,?)",
                user.id(), user.username(), user.displayName(), user.roleCode(), user.status(), user.createdAt(), user.passwordHash());
    }
    public void deleteUser(long userId) {
        userPermissions.remove(userId);
        if (jdbc != null) jdbc.update("DELETE FROM platform_user WHERE id=?", userId);
    }
    public void persistSeaTunnelCluster(SeaTunnelClusterView cluster, String encryptedPassword) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE seatunnel_cluster SET name=?,host=?,port=?,ssh_username=?,ssh_port=?,ssh_password_encrypted=?,seatunnel_home=?,description=?,health_status=? WHERE id=?",
                cluster.name(), cluster.host(), cluster.port(), cluster.sshUsername(), cluster.sshPort(), encryptedPassword,
                cluster.seatunnelHome(), cluster.description(), cluster.healthStatus(), cluster.id());
        if (updated == 0) jdbc.update("INSERT INTO seatunnel_cluster (id,name,host,port,ssh_username,ssh_port,ssh_password_encrypted,seatunnel_home,description,health_status,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                cluster.id(), cluster.name(), cluster.host(), cluster.port(), cluster.sshUsername(), cluster.sshPort(), encryptedPassword,
                cluster.seatunnelHome(), cluster.description(), cluster.healthStatus(), cluster.createdAt());
    }
    public void deleteSeaTunnelCluster(long id) {
        if (jdbc != null) jdbc.update("DELETE FROM seatunnel_cluster WHERE id=?", id);
    }
    public void persistUserPermissions(long userId, Set<String> permissions) {
        userPermissions.put(userId, ConcurrentHashMap.newKeySet());
        userPermissions.get(userId).addAll(permissions);
        if (jdbc == null) return;
        jdbc.update("DELETE FROM user_permission WHERE user_id=?", userId);
        for (String permission : permissions)
            jdbc.update("INSERT INTO user_permission (user_id,permission_code) VALUES (?,?)", userId, permission);
    }
    public void persistQueryExecution(String queryId, Long datasourceId, String databaseName, String sql,
                                      String status, LocalDateTime startedAt, LocalDateTime finishedAt, long elapsedMs, String error) {
        if (jdbc == null) return;
        jdbc.update("INSERT INTO query_execution (query_id,datasource_id,database_name,sql_text,status,started_at,finished_at,elapsed_ms,error_message) VALUES (?,?,?,?,?,?,?,?,?)",
                queryId, datasourceId, databaseName, sql, status, startedAt, finishedAt, elapsedMs, error);
    }
    public List<QueryHistoryView> queryHistory() {
        if (jdbc == null) return List.of();
        return jdbc.query("SELECT query_id,datasource_id,database_name,sql_text,status,started_at,finished_at,elapsed_ms,error_message FROM query_execution ORDER BY started_at DESC LIMIT 100", (rs, rowNum) -> {
            var started = rs.getTimestamp("started_at");
            var finished = rs.getTimestamp("finished_at");
            Long datasourceId = rs.getObject("datasource_id", Long.class);
            return new QueryHistoryView(rs.getString("query_id"), datasourceId, rs.getString("database_name"),
                    rs.getString("sql_text"), rs.getString("status"), started == null ? null : started.toLocalDateTime(),
                    finished == null ? null : finished.toLocalDateTime(), rs.getLong("elapsed_ms"), rs.getString("error_message"));
        });
    }
    public void persistWorkflow(WorkflowView workflow) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE workflow SET name=?,description=?,status=?,ds_process_code=?,published_version=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                workflow.name(), workflow.description(), workflow.status(), workflow.dsProcessCode(), workflow.publishedVersion(), workflow.id());
        if (updated == 0) jdbc.update("INSERT INTO workflow (id,name,workflow_code,description,status,ds_process_code,published_version) VALUES (?,?,?,?,?,?,?)",
                workflow.id(), workflow.name(), workflow.workflowCode(), workflow.description(), workflow.status(), workflow.dsProcessCode(), workflow.publishedVersion());
        jdbc.update("DELETE FROM workflow_node WHERE workflow_id=?", workflow.id());
        jdbc.update("DELETE FROM workflow_edge WHERE workflow_id=?", workflow.id());
        for (WorkflowNodeView node : workflow.nodes()) {
            jdbc.update("INSERT INTO workflow_node (id,workflow_id,node_code,node_name,node_type,file_version_id,config_json,x,y) VALUES (?,?,?,?,?,?,?,?,?)",
                    node.id(), workflow.id(), node.nodeCode(), node.name(), node.nodeType().name(), node.fileVersionId(), node.configJson(), node.x(), node.y());
        }
        for (WorkflowEdgeView edge : workflow.edges()) {
            jdbc.update("INSERT INTO workflow_edge (id,workflow_id,source_node_id,target_node_id) VALUES (?,?,?,?)",
                    edge.id(), workflow.id(), edge.sourceNodeId(), edge.targetNodeId());
        }
    }
    public void persistSchedule(ScheduleConfigView schedule) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE schedule_config SET workflow_id=?,cron_expression=?,timezone=?,enabled=?,failure_strategy=?,parallelism=?,worker_group=?,alert_group=?,ds_schedule_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                schedule.workflowId(), schedule.cronExpression(), schedule.timezone(), schedule.enabled(), schedule.failureStrategy(), schedule.parallelism(), schedule.workerGroup(), schedule.alertGroup(), schedule.dsScheduleId(), schedule.id());
        if (updated == 0) jdbc.update("INSERT INTO schedule_config (id,workflow_id,cron_expression,timezone,enabled,failure_strategy,parallelism,worker_group,alert_group,ds_schedule_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                schedule.id(), schedule.workflowId(), schedule.cronExpression(), schedule.timezone(), schedule.enabled(), schedule.failureStrategy(), schedule.parallelism(), schedule.workerGroup(), schedule.alertGroup(), schedule.dsScheduleId());
    }
    public void persistRole(RoleView role) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE platform_role SET role_code=?,role_name=? WHERE id=?", role.roleCode(), role.roleName(), role.id());
        if (updated == 0) jdbc.update("INSERT INTO platform_role (id,role_code,role_name) VALUES (?,?,?)", role.id(), role.roleCode(), role.roleName());
        jdbc.update("DELETE FROM role_permission WHERE role_id=?", role.id());
        for (String permission : role.permissions()) jdbc.update("INSERT INTO role_permission (role_id,permission_code) VALUES (?,?)", role.id(), permission);
    }
    public void persistAlertChannel(AlertChannelView channel) {
        if (jdbc == null) return;
        int updated = jdbc.update("UPDATE alert_channel SET name=?,channel_type=?,config_json=?,enabled=? WHERE id=?", channel.name(), channel.channelType(), channel.configJson(), channel.enabled(), channel.id());
        if (updated == 0) jdbc.update("INSERT INTO alert_channel (id,name,channel_type,config_json,enabled) VALUES (?,?,?,?,?)", channel.id(), channel.name(), channel.channelType(), channel.configJson(), channel.enabled());
    }
    public void persistProjectPermission(long projectId, long userId, String permission) {
        if (jdbc == null) return;
        jdbc.update("INSERT INTO project_member (project_id,user_id,permission_code) VALUES (?,?,?)", projectId, userId, permission);
    }
    public void persistDatasourcePermission(long datasourceId, long userId, String permission) {
        if (jdbc == null) return;
        jdbc.update("INSERT INTO datasource_permission (datasource_id,user_id,permission_code) VALUES (?,?,?)", datasourceId, userId, permission);
    }
    private void advanceId(long id) { ids.updateAndGet(current -> Math.max(current, id)); }

    /** Keep generated IDs above records that were created by an earlier process instance. */
    private void refreshIdSequence() {
        if (jdbc == null) return;
        for (String table : List.of("data_source", "dev_project", "dev_folder", "dev_file", "dev_file_version",
                "integration_task", "integration_instance", "workflow", "workflow_node", "workflow_edge",
                "integration_task_table", "platform_user", "platform_role", "alert_channel", "operation_audit", "schedule_config")) {
            try {
                Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
                if (max != null) advanceId(max);
            } catch (RuntimeException ignored) {
                // One unavailable optional table must not prevent the remaining data from loading.
            }
        }
    }

    public PlatformStore() {
        long projectId = nextId();
        projects.put(projectId, new DevProjectView(projectId, "示例项目", "平台内置演示项目", "ACTIVE"));
        long folderId = nextId();
        folders.put(folderId, new DevFolderView(folderId, projectId, null, "analytics"));
        long fileId = nextId();
        String content = "SELECT order_date, SUM(amount) AS total_amount FROM sales.orders GROUP BY order_date;";
        files.put(fileId, new DevFileView(fileId, projectId, folderId, "daily_sales.sql", "SQL", content, "DRAFT", 1));
        long versionId = nextId();
        versions.put(versionId, new FileVersionView(versionId, fileId, 1, content, "seed", false));
    }
}
