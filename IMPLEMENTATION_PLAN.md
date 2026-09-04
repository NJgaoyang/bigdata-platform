# 大数据开发平台实施执行计划

> 目标：基于 **Vue 3 + Monaco Editor + Element Plus + AntV X6 + Java 21 + Spring Boot + MySQL 8 + StarRocks 3.3.22 + SeaTunnel 2.3.12 + DolphinScheduler 3.1.9** 实现统一大数据开发平台。
>
> 平台自己实现 UI、开发工作台、工作流设计、调度配置、运维与血缘展示；**DolphinScheduler 仅作为底层调度执行引擎**；**SeaTunnel 2.3.12 作为离线数据同步执行引擎**。

---

## 1. 总体目标

最终平台模块：

```text
大数据开发平台

数据集成
├── 离线同步
└── 实时同步 featureFlag=false

数据开发
├── 项目
├── 文件夹
├── SQL
├── Shell
└── Python

数据探查
├── 元数据树
├── SQL Editor
├── SQL运行
├── SQL停止
├── 选中SQL运行
└── 表结构/中文注释

数据血缘
├── 表级血缘
└── 后续扩展字段级血缘

调度中心
├── 工作流
├── 调度配置
├── 工作流实例
└── 补数据

运维中心
├── 任务实例
├── 失败任务
├── 日志
└── 告警

系统配置
├── 用户
├── 角色
├── 项目空间
├── 数据源
├── 告警渠道
└── Feature Flag
```

---

# 2. 总体技术架构

```text
┌──────────────────────────────────────────────────────────────┐
│                     大数据开发平台                           │
│                                                              │
│ 数据集成 │ 数据开发 │ 数据探查 │ 数据血缘 │ 调度 │ 运维 │ 配置 │
└──────────────────────────────┬───────────────────────────────┘
                               │
                         Vue 3 + Monaco
                         Element Plus
                         AntV X6
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                     Spring Boot                              │
│                                                              │
│ DataSourceService                                            │
│ DevelopmentService                                           │
│ WorkflowService                                              │
│ WorkflowPublishService                                       │
│ SchedulerService                                             │
│ OperationService                                             │
│ MetadataService                                              │
│ LineageService                                               │
│ IntegrationService                                           │
│                                                              │
│ DolphinSchedulerGateway                                      │
│ SeaTunnelGateway                                             │
└───────────┬──────────────────────┬───────────────────────────┘
            │                      │
            ▼                      ▼
      MySQL 平台库           DolphinScheduler API
            │                      │
            │                      ▼
            │             DolphinScheduler Master
            │                      │
            │                      ▼
            │             DolphinScheduler Worker
            │                      │
            │         ┌────────────┼────────────┐
            │         ▼            ▼            ▼
            │       SQL          Python       Shell
            │         │
            │         ▼
            │     StarRocks
            │
            └──────────────► SeaTunnel 2.3.12
                               │
                               ▼
                       MySQL → StarRocks
                       StarRocks → StarRocks
```

---

# 3. 固定技术栈

## 前端

| 技术 | 用途 |
|---|---|
| Vue 3 | 主前端框架 |
| TypeScript | 类型安全 |
| Vite | 构建工具 |
| Element Plus | 表格、弹窗、表单、分页 |
| Monaco Editor | SQL / Python / Shell 编辑 |
| AntV X6 | 工作流 DAG / 数据血缘 |
| Pinia | 前端状态 |
| Vue Router | 页面路由 |
| Axios | HTTP |
| Splitpanes | SQL IDE 左右、上下布局拖拽 |

## 后端

| 技术 | 用途 |
|---|---|
| Java 21 | 主开发语言 |
| Maven | 构建 |
| Spring Boot | 单体服务 |
| Spring Web | REST API |
| MyBatis / MyBatis-Plus | 平台元数据 CRUD |
| HikariCP | 动态数据源连接池 |
| Alibaba Druid Parser | SQL AST、血缘、危险SQL检测 |
| Flyway | 平台库结构迁移 |
| SpringDoc OpenAPI | Swagger |
| Jackson | JSON |
| SLF4J + Logback | 日志 |

## 数据与执行

| 技术 | 版本/用途 |
|---|---|
| MySQL | 8.x，平台系统元数据 |
| StarRocks | 3.3.22 |
| SeaTunnel | 2.3.12，离线同步 |
| DolphinScheduler | 3.1.9，底层工作流/调度执行 |

> DolphinScheduler 已固定为 3.1.9，安装目录为 `/data/software/dolphinscheduler`，账号为 `admin`。不允许将 DS API DTO 散落在业务代码中，统一通过 `DolphinSchedulerGateway` 隔离。

---

# 4. 开发环境配置

## MySQL 平台元数据库

开发环境：

```yaml
spring:
  datasource:
    url: jdbc:mysql://192.168.113.135:3306/bigdata_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: "123456"
```

首次执行：

```sql
CREATE DATABASE IF NOT EXISTS bigdata_platform
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_general_ci;
```

## 外部执行服务

开发阶段暂不启动 DolphinScheduler 和 SeaTunnel。Phase 0 至 Phase 10 优先完成代码、Mock/Stub、单元测试、构建和前端页面验证；全部开发完成后，再在最终验收阶段启动真实服务进行联调。

```text
DolphinScheduler 版本：3.1.9
DolphinScheduler 安装目录：/data/software/dolphinscheduler
DolphinScheduler 账号：admin
SeaTunnel 版本：2.3.12
SeaTunnel 安装目录：/data/software/seatunnel
```

> 以上账号密码仅作为你当前开发环境默认配置。正式环境必须改成环境变量或 Secret，不允许将真实密码提交到 Git。

---

# 5. Maven 单体工程建议

```text
bigdata-platform/
├── pom.xml
├── README.md
├── db/
│   └── migration/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
└── src/
    └── main/
        ├── java/com/company/platform/
        │   ├── PlatformApplication.java
        │   ├── common/
        │   ├── config/
        │   ├── controller/
        │   ├── datasource/
        │   ├── development/
        │   ├── metadata/
        │   ├── query/
        │   ├── integration/
        │   ├── lineage/
        │   ├── workflow/
        │   ├── scheduler/
        │   ├── operation/
        │   └── system/
        └── resources/
            ├── application.yml
            └── db/migration/
```

后端保持**单体应用**，第一阶段不要拆微服务。

---

# 6. 后端核心分层

```text
Controller
    ↓
Application Service
    ↓
Domain / Business Service
    ↓
Repository / Gateway
    ↓
MySQL / JDBC / DolphinScheduler / SeaTunnel
```

核心接口：

```java
public interface SchedulerGateway {
    PublishResult publishWorkflow(PublishedWorkflow workflow);
    void onlineWorkflow(String processCode);
    String runWorkflow(String processCode, Map<String, String> params);
    String backfill(String processCode, LocalDateTime start, LocalDateTime end, int parallelism);
    void stopProcessInstance(String instanceCode);
    List<ProcessInstanceDTO> listProcessInstances(ProcessInstanceQuery query);
    List<TaskInstanceDTO> listTaskInstances(TaskInstanceQuery query);
    String getTaskLog(String taskInstanceCode);
}
```

SeaTunnel：

```java
public interface SeaTunnelGateway {
    ValidationResult validate(SeaTunnelJobConfig config);
    SeaTunnelSubmitResult submit(SeaTunnelJobConfig config);
    SeaTunnelJobStatus status(String executionId);
    void cancel(String executionId);
    String log(String executionId);
}
```

---

# 7. 平台数据库设计

## 7.1 数据源

```sql
CREATE TABLE data_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    jdbc_url VARCHAR(1000),
    host VARCHAR(255),
    port INT,
    username VARCHAR(255),
    password_ciphertext TEXT,
    default_database VARCHAR(255),
    properties_json JSON,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_ds_name(name)
);
```

---

## 7.2 项目

```sql
CREATE TABLE dev_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    description VARCHAR(1000),
    owner_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_project_code(project_code)
);
```

---

## 7.3 文件夹

```sql
CREATE TABLE dev_folder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    folder_name VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_folder_project(project_id),
    KEY idx_folder_parent(parent_id)
);
```

---

## 7.4 开发文件

```sql
CREATE TABLE dev_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    folder_id BIGINT,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    content LONGTEXT,
    datasource_id BIGINT,
    database_name VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_file_project(project_id),
    KEY idx_file_folder(folder_id)
);
```

`file_type`：

```text
SQL
PYTHON
SHELL
```

---

## 7.5 文件版本

```sql
CREATE TABLE dev_file_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    content LONGTEXT NOT NULL,
    checksum VARCHAR(64),
    publish_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_file_version(file_id, version_no)
);
```

---

## 7.6 SQL运行记录

```sql
CREATE TABLE query_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    query_id VARCHAR(64) NOT NULL,
    user_id BIGINT,
    datasource_id BIGINT NOT NULL,
    database_name VARCHAR(255),
    sql_text LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME,
    finished_at DATETIME,
    elapsed_ms BIGINT,
    error_message TEXT,
    UNIQUE KEY uk_query_id(query_id)
);
```

---

## 7.7 工作流

```sql
CREATE TABLE workflow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    workflow_code VARCHAR(64) NOT NULL,
    workflow_name VARCHAR(128) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    ds_process_code VARCHAR(128),
    published_version INT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_workflow_code(workflow_code)
);
```

---

## 7.8 工作流节点

```sql
CREATE TABLE workflow_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    node_code VARCHAR(64) NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    dev_file_id BIGINT,
    file_version_id BIGINT,
    config_json JSON,
    x INT,
    y INT,
    timeout_seconds INT,
    retry_times INT DEFAULT 0,
    retry_interval_seconds INT DEFAULT 60,
    ds_task_code VARCHAR(128),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_workflow_node(workflow_id, node_code)
);
```

---

## 7.9 工作流边

```sql
CREATE TABLE workflow_edge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_workflow_edge(workflow_id, source_node_id, target_node_id)
);
```

---

## 7.10 调度配置

```sql
CREATE TABLE schedule_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    cron_expression VARCHAR(128) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    enabled TINYINT NOT NULL DEFAULT 0,
    failure_strategy VARCHAR(32) DEFAULT 'END',
    worker_group VARCHAR(128),
    alert_group VARCHAR(128),
    ds_schedule_id VARCHAR(128),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
```

---

## 7.11 离线同步任务

```sql
CREATE TABLE integration_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT,
    task_name VARCHAR(128) NOT NULL,
    source_datasource_id BIGINT NOT NULL,
    target_datasource_id BIGINT NOT NULL,
    source_config_json JSON NOT NULL,
    target_config_json JSON NOT NULL,
    transform_config_json JSON,
    seatunnel_config LONGTEXT,
    sync_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
```

---

## 7.12 离线同步实例

```sql
CREATE TABLE integration_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    execution_id VARCHAR(128),
    ds_process_instance_code VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    started_at DATETIME,
    finished_at DATETIME,
    elapsed_ms BIGINT,
    error_message TEXT,
    created_at DATETIME NOT NULL
);
```

---

## 7.13 血缘关系

```sql
CREATE TABLE data_lineage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_datasource_id BIGINT,
    source_database VARCHAR(255),
    source_table VARCHAR(255) NOT NULL,
    target_datasource_id BIGINT,
    target_database VARCHAR(255),
    target_table VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) DEFAULT 'TABLE',
    relation_type VARCHAR(32) DEFAULT 'SQL',
    dev_file_id BIGINT,
    file_version_id BIGINT,
    workflow_id BIGINT,
    sql_hash VARCHAR(64),
    created_at DATETIME NOT NULL,
    KEY idx_lineage_source(source_database, source_table),
    KEY idx_lineage_target(target_database, target_table)
);
```

---

# 8. 数据开发实现

## 8.1 左侧资源树

接口：

```http
GET    /api/projects
POST   /api/projects
PUT    /api/projects/{id}
DELETE /api/projects/{id}

GET    /api/projects/{projectId}/tree

POST   /api/folders
PUT    /api/folders/{id}
DELETE /api/folders/{id}

POST   /api/files
GET    /api/files/{id}
PUT    /api/files/{id}
DELETE /api/files/{id}
```

删除文件夹伪代码：

```java
@Transactional
public void deleteFolder(Long folderId) {
    long fileCount = fileRepository.countByFolderId(folderId);
    long childFolderCount = folderRepository.countByParentId(folderId);

    if (fileCount > 0 || childFolderCount > 0) {
        throw new BizException("文件夹下存在文件或子文件夹，不能删除");
    }

    folderRepository.deleteById(folderId);
}
```

---

## 8.2 Monaco Editor

前端需要实现：

```text
SQL
Python
Shell

语法高亮
行号
代码折叠
搜索替换
Ctrl+S
多Tab
Dirty状态
代码补全
选中执行
格式化
```

伪代码：

```ts
const editor = monaco.editor.create(container, {
  value: file.content,
  language: resolveLanguage(file.fileType),
  automaticLayout: true,
  minimap: { enabled: false },
  fontSize: 14
})
```

保存：

```ts
editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, async () => {
  await saveFile({
    id: currentFile.id,
    content: editor.getValue()
  })
})
```

---

# 9. SQL代码补全

后端：

```http
GET /api/metadata/datasources/{id}/databases
GET /api/metadata/datasources/{id}/tables?database=xxx
GET /api/metadata/datasources/{id}/columns?database=xxx&table=xxx
```

前端：

```ts
monaco.languages.registerCompletionItemProvider('sql', {
  provideCompletionItems(model, position) {
    const metadata = metadataStore.current
    return {
      suggestions: buildSuggestions(metadata)
    }
  }
})
```

自动补全内容：

```text
SELECT
FROM
WHERE
GROUP BY
ORDER BY

数据库名
表名
字段名

COUNT
SUM
AVG
DATE_FORMAT
COALESCE
```

---

# 10. 数据探查

界面：

```text
┌────────────────┬───────────────────────────────┬───────────────┐
│ 数据库元数据树  │ Monaco SQL Editor             │ 表结构         │
│                │                               │               │
│ starrocks_prod │ ▶运行 ■停止                   │ 字段           │
│ ├─ods          │                               │ 类型           │
│ │ └─table      │                               │ 中文注释       │
│ ├─dwd          ├───────────────────────────────┤               │
│ └─ads          │ ResultSet                     │               │
└────────────────┴───────────────────────────────┴───────────────┘
```

StarRocks/MySQL 元数据：

```sql
SELECT
    TABLE_SCHEMA,
    TABLE_NAME,
    TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA NOT IN ('information_schema');
```

字段：

```sql
SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_COMMENT,
    ORDINAL_POSITION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = ?
  AND TABLE_NAME = ?
ORDER BY ORDINAL_POSITION;
```

---

# 11. SQL执行引擎

不要使用 MyBatis 执行业务 SQL。

使用 JDBC。

核心结构：

```text
QueryController
    ↓
QueryService
    ↓
DynamicDataSourceManager
    ↓
java.sql.Connection
    ↓
java.sql.Statement
    ↓
StarRocks / MySQL
```

执行伪代码：

```java
public QueryResult execute(QueryRequest request) {
    String queryId = UUID.randomUUID().toString();

    Connection connection =
        dynamicDataSourceManager.getConnection(request.datasourceId());

    Statement statement = connection.createStatement();

    runningStatementRegistry.register(queryId, statement);

    long start = System.currentTimeMillis();

    try {
        boolean hasResult = statement.execute(request.sql());

        if (!hasResult) {
            return QueryResult.updateCount(
                queryId,
                statement.getUpdateCount()
            );
        }

        try (ResultSet rs = statement.getResultSet()) {
            return resultSetConverter.convert(
                queryId,
                rs,
                request.maxRows()
            );
        }
    } finally {
        runningStatementRegistry.remove(queryId);
        statement.close();
        connection.close();
    }
}
```

---

# 12. SQL停止

```java
@Component
public class RunningStatementRegistry {

    private final ConcurrentHashMap<String, Statement> running =
        new ConcurrentHashMap<>();

    public void register(String queryId, Statement statement) {
        running.put(queryId, statement);
    }

    public void cancel(String queryId) throws SQLException {
        Statement statement = running.get(queryId);

        if (statement == null) {
            throw new BizException("查询不存在或已结束");
        }

        statement.cancel();
    }

    public void remove(String queryId) {
        running.remove(queryId);
    }
}
```

接口：

```http
POST /api/query/execute
POST /api/query/{queryId}/cancel
```

---

# 13. 选中SQL运行

前端：

```ts
function getSqlToExecute(editor: monaco.editor.IStandaloneCodeEditor) {
    const model = editor.getModel()!
    const selection = editor.getSelection()!

    if (!selection.isEmpty()) {
        return model.getValueInRange(selection)
    }

    return model.getValue()
}
```

---

# 14. SQL安全控制

建议至少拦截：

```text
DROP DATABASE
DROP TABLE
TRUNCATE
DELETE 无 WHERE
UPDATE 无 WHERE
```

Druid Parser：

```java
public SqlCheckResult check(String sql) {

    SQLStatement statement =
        SQLUtils.parseSingleStatement(sql, DbType.mysql);

    if (statement instanceof SQLDropDatabaseStatement) {
        return deny("禁止执行 DROP DATABASE");
    }

    if (statement instanceof SQLDeleteStatement delete) {
        if (delete.getWhere() == null) {
            return deny("DELETE 必须包含 WHERE");
        }
    }

    return allow();
}
```

开发环境可以允许管理员绕过，但必须审计。

---

# 15. 离线同步：SeaTunnel 2.3.12

数据集成 UI：

```text
任务名称

来源
├── 数据源
├── 数据库
├── 表
├── 字段
└── where条件

目标
├── 数据源
├── 数据库
├── 表
├── 写入模式
└── 字段映射

运行参数
├── 并行度
├── batch size
└── 错误策略
```

平台将 UI 配置转换成 SeaTunnel 配置。

伪代码：

```java
public SeaTunnelJobConfig buildConfig(IntegrationTask task) {

    SourceConfig source =
        sourceBuilder.build(task.getSourceConfig());

    SinkConfig sink =
        sinkBuilder.build(task.getTargetConfig());

    return new SeaTunnelJobConfig(
        envConfig(task),
        source,
        transformBuilder.build(task.getTransformConfig()),
        sink
    );
}
```

示例逻辑配置：

```hocon
env {
  parallelism = 4
  job.mode = "BATCH"
}

source {
  Jdbc {
    url = "jdbc:mysql://mysql-host:3306/app"
    driver = "com.mysql.cj.jdbc.Driver"
    user = "xxx"
    password = "xxx"
    query = "select * from order_info"
  }
}

sink {
  StarRocks {
    node_urls = ["starrocks-fe:8030"]
    jdbc_url = "jdbc:mysql://starrocks-fe:9030"
    database = "ods"
    table = "ods_order_info"
    username = "root"
    password = ""
  }
}
```

> 真实参数必须以 SeaTunnel 2.3.12 对应 connector 文档为准；生成代码时将 connector 配置集中在 `seatunnel` 模块中，不允许散落在 Controller。

---

# 16. SeaTunnel 与 DolphinScheduler 如何组合

推荐：

```text
平台数据集成 UI
      ↓
生成 SeaTunnel Config
      ↓
发布为工作流节点
      ↓
DolphinScheduler
      ↓
SeaTunnel Task / Shell Task
      ↓
SeaTunnel Engine
```

如果你的 DolphinScheduler 当前环境没有可直接使用的 SeaTunnel Task Plugin：

```text
DolphinScheduler Shell Task
        ↓
seatunnel.sh -c generated.conf
```

不要因此自己实现调度器。

平台保存：

```text
integration_task
      ↓
seatunnel_config
      ↓
发布时创建/更新 DS 节点
```

---

# 17. 工作流 UI

前端使用 AntV X6。

支持节点：

```text
SQL
Shell
Python
SeaTunnel离线同步
```

后续可扩展：

```text
HTTP
条件节点
子工作流
依赖检查
```

保存时仅写平台数据库：

```text
workflow
workflow_node
workflow_edge
```

不要在“保存”时直接写 DolphinScheduler。

---

# 18. 工作流保存与发布分离

## 保存

```text
UI
 ↓
POST /api/workflows/{id}/save
 ↓
MySQL
```

## 发布

```text
UI
 ↓
POST /api/workflows/{id}/publish
 ↓
校验
 ↓
生成版本快照
 ↓
转换 DS TaskDefinition
 ↓
转换 DS ProcessDefinition
 ↓
DolphinSchedulerGateway
 ↓
DolphinScheduler
```

---

# 19. 发布版本快照

必须做到：

```text
开发态
dwd_order.sql V9
      ↓
发布
      ↓
生产态 V9 快照

后来编辑成 V10
      ↓
不会影响生产态 V9
```

伪代码：

```java
@Transactional
public PublishSnapshot snapshotWorkflow(Long workflowId) {

    Workflow wf = workflowRepository.get(workflowId);

    List<WorkflowNode> nodes =
        workflowNodeRepository.list(workflowId);

    for (WorkflowNode node : nodes) {

        if (node.getDevFileId() == null) {
            continue;
        }

        DevFile file =
            devFileRepository.get(node.getDevFileId());

        DevFileVersion version =
            versionService.createVersion(file);

        node.bindPublishedVersion(version.getId());
    }

    return new PublishSnapshot(wf, nodes);
}
```

---

# 20. DolphinScheduler Adapter

不要业务代码直接依赖 DS API。

```text
WorkflowPublishService
        ↓
SchedulerGateway
        ↓
DolphinSchedulerGatewayImpl
        ↓
DS Rest Client
```

DTO 转换：

```java
public interface DsTaskConverter<T extends NodeConfig> {

    boolean supports(NodeType type);

    DsTaskDefinition convert(
        PublishedWorkflowNode node,
        T config
    );
}
```

实现：

```text
SqlDsTaskConverter
ShellDsTaskConverter
PythonDsTaskConverter
SeaTunnelDsTaskConverter
```

---

# 21. 发布工作流伪代码

```java
@Transactional
public PublishResult publish(Long workflowId) {

    Workflow workflow =
        workflowRepository.getRequired(workflowId);

    List<WorkflowNode> nodes =
        nodeRepository.listByWorkflowId(workflowId);

    List<WorkflowEdge> edges =
        edgeRepository.listByWorkflowId(workflowId);

    dagValidator.validate(nodes, edges);

    PublishSnapshot snapshot =
        publishSnapshotService.create(workflow, nodes, edges);

    List<DsTaskDefinition> dsTasks =
        dsTaskConverterRegistry.convert(snapshot);

    DsProcessDefinition dsProcess =
        dsProcessConverter.convert(
            snapshot,
            dsTasks
        );

    PublishResult result =
        schedulerGateway.publishWorkflow(
            snapshot.toPublishedWorkflow()
        );

    workflow.markPublished(
        result.processCode(),
        snapshot.version()
    );

    workflowRepository.update(workflow);

    return result;
}
```

---

# 22. DAG校验

发布前：

```text
至少1个节点
不能存在孤立异常节点
不能存在环
边引用节点必须存在
文件必须存在
发布文件必须生成版本
SQL必须通过Parser
数据源必须可用
```

拓扑排序：

```java
public void validateAcyclic(
        List<Node> nodes,
        List<Edge> edges) {

    Map<Long, Integer> indegree = new HashMap<>();

    for (Node node : nodes) {
        indegree.put(node.id(), 0);
    }

    for (Edge edge : edges) {
        indegree.compute(
            edge.target(),
            (k, v) -> v + 1
        );
    }

    Queue<Long> queue = new ArrayDeque<>();

    indegree.forEach((id, degree) -> {
        if (degree == 0) {
            queue.add(id);
        }
    });

    int visited = 0;

    while (!queue.isEmpty()) {
        Long node = queue.poll();
        visited++;

        for (Edge edge : outgoing(node, edges)) {
            int d = indegree.compute(
                edge.target(),
                (k, v) -> v - 1
            );

            if (d == 0) {
                queue.add(edge.target());
            }
        }
    }

    if (visited != nodes.size()) {
        throw new BizException("工作流存在循环依赖");
    }
}
```

---

# 23. 调度配置

UI 不显示 DolphinScheduler 页面。

平台：

```text
每天
每周
每月
Cron
```

保存：

```text
schedule_config
```

上线时：

```text
schedule_config
      ↓
SchedulerService
      ↓
SchedulerGateway
      ↓
DolphinScheduler Schedule API
```

---

# 24. 补数据

UI：

```text
工作流
开始业务时间
结束业务时间
并行度
失败策略
```

后端：

```java
public String backfill(
        Long workflowId,
        LocalDateTime start,
        LocalDateTime end,
        int parallelism) {

    Workflow wf =
        workflowRepository.getRequired(workflowId);

    ensurePublished(wf);

    return schedulerGateway.backfill(
        wf.getDsProcessCode(),
        start,
        end,
        parallelism
    );
}
```

---

# 25. 运维中心

平台自己做：

```text
工作流实例
任务实例
失败任务
日志
停止
重跑
```

数据来自：

```text
OperationService
      ↓
SchedulerGateway
      ↓
DolphinScheduler API
```

第一期不需要同步全部 DS 实例到平台 MySQL。

当数据量变大再增加：

```text
instance_sync_job
```

---

# 26. 数据血缘

SQL来源：

```text
开发SQL
发布版本SQL
工作流节点SQL
```

Parser：

```text
Alibaba Druid
```

流程：

```text
SQL
 ↓
AST
 ↓
source tables
 ↓
target tables
 ↓
data_lineage
 ↓
AntV X6
```

伪代码：

```java
public ParsedLineage parse(String sql) {

    SQLStatement statement =
        SQLUtils.parseSingleStatement(
            sql,
            DbType.mysql
        );

    Set<TableRef> sources =
        sourceTableVisitor.collect(statement);

    Set<TableRef> targets =
        targetTableVisitor.collect(statement);

    return new ParsedLineage(
        sources,
        targets
    );
}
```

发布时：

```text
生成 file version
      ↓
解析版本SQL
      ↓
写 lineage
```

保证血缘与生产版本一致。

---

# 27. API清单

## 数据源

```text
GET    /api/datasources
POST   /api/datasources
PUT    /api/datasources/{id}
DELETE /api/datasources/{id}
POST   /api/datasources/{id}/test
```

## 数据开发

```text
GET    /api/projects
POST   /api/projects
PUT    /api/projects/{id}
DELETE /api/projects/{id}

GET    /api/projects/{id}/tree

POST   /api/folders
PUT    /api/folders/{id}
DELETE /api/folders/{id}

POST   /api/files
GET    /api/files/{id}
PUT    /api/files/{id}
DELETE /api/files/{id}
POST   /api/files/{id}/versions
GET    /api/files/{id}/versions
```

## 数据探查

```text
GET  /api/metadata/datasources/{id}/databases
GET  /api/metadata/datasources/{id}/tables
GET  /api/metadata/datasources/{id}/columns

POST /api/query/execute
POST /api/query/{queryId}/cancel
```

## 数据集成

```text
GET    /api/integration/tasks
POST   /api/integration/tasks
PUT    /api/integration/tasks/{id}
DELETE /api/integration/tasks/{id}
POST   /api/integration/tasks/{id}/validate
POST   /api/integration/tasks/{id}/run
POST   /api/integration/tasks/{id}/stop
GET    /api/integration/tasks/{id}/instances
```

## 工作流

```text
GET    /api/workflows
POST   /api/workflows
GET    /api/workflows/{id}
PUT    /api/workflows/{id}
DELETE /api/workflows/{id}

PUT    /api/workflows/{id}/graph
POST   /api/workflows/{id}/validate
POST   /api/workflows/{id}/publish
POST   /api/workflows/{id}/run
POST   /api/workflows/{id}/backfill
```

## 调度

```text
GET /api/workflows/{id}/schedule
PUT /api/workflows/{id}/schedule
POST /api/workflows/{id}/schedule/online
POST /api/workflows/{id}/schedule/offline
```

## 运维

```text
GET  /api/operations/process-instances
GET  /api/operations/task-instances
GET  /api/operations/failed-tasks
GET  /api/operations/task-instances/{id}/log
POST /api/operations/process-instances/{id}/stop
POST /api/operations/process-instances/{id}/rerun
```

## 血缘

```text
GET /api/lineage/table
GET /api/lineage/file/{fileId}
GET /api/lineage/workflow/{workflowId}
```

---

# 28. 前端路由

```text
/integration
/development
/explore
/lineage
/schedule
/operation
/system
```

目录：

```text
frontend/src/
├── api/
├── components/
│   ├── MonacoEditor/
│   ├── ProjectResourceTree/
│   ├── MetadataTree/
│   ├── ResultGrid/
│   ├── WorkflowCanvas/
│   └── LineageGraph/
├── layouts/
│   └── MainLayout.vue
├── stores/
│   ├── editor.ts
│   ├── project.ts
│   ├── datasource.ts
│   └── workflow.ts
└── views/
    ├── integration/
    ├── development/
    ├── explore/
    ├── lineage/
    ├── schedule/
    ├── operation/
    └── system/
```

---

# 29. Feature Flag

后端：

```yaml
platform:
  features:
    realtime-sync: false
```

前端获取：

```http
GET /api/system/features
```

响应：

```json
{
  "realtimeSync": false
}
```

前端：

```ts
if (!features.realtimeSync) {
  hideRealtimeSyncMenu()
}
```

---

# 30. 实施阶段

## Phase 0：工程骨架

完成：

```text
Maven
Spring Boot
Vue 3
Vite
Element Plus
MySQL
Flyway
统一异常
统一 Result
Swagger
```

验收：

```text
mvn clean package 成功
前端 npm run build 成功
后端能连接 192.168.113.135 MySQL
```

---

## Phase 1：主布局 + 数据源

完成：

```text
顶部导航
数据源 CRUD
连接测试
密码加密
动态连接池
```

验收：

```text
能新增 MySQL
能新增 StarRocks
能测试连接
能动态取得 JDBC Connection
```

---

## Phase 2：数据开发

完成：

```text
项目 CRUD
文件夹 CRUD
文件 CRUD
资源树
文件夹非空不能删
Monaco Editor
SQL / Python / Shell
多Tab
保存
版本
```

验收：

```text
完整模拟原型图开发体验
Ctrl+S 可保存
多文件切换不丢内容
```

---

## Phase 3：数据探查

完成：

```text
元数据树
搜索表
字段+中文注释
SQL运行
停止
选中运行
结果集
```

验收：

```text
StarRocks 3.3.22 查询成功
MySQL 查询成功
Statement.cancel() 可停止查询
```

---

## Phase 4：离线同步

完成：

```text
SeaTunnel 2.3.12
任务配置
字段映射
生成 SeaTunnel Config
测试执行（开发阶段使用 Mock/Stub；最终验收阶段执行真实任务）
状态/日志
```

验收：

```text
开发阶段：使用 Mock/Stub 验证配置生成、提交、状态和日志流程。
最终验收阶段：启动 SeaTunnel 后，MySQL → StarRocks 离线同步成功。
```

---

## Phase 5：工作流

完成：

```text
AntV X6
SQL节点
Shell节点
Python节点
SeaTunnel节点
节点连线
DAG校验
保存工作流
```

---

## Phase 6：发布 + DolphinScheduler 3.1.9

完成：

```text
文件版本快照
工作流版本
SchedulerGateway
DS Task Converter
DS Process Converter
发布
上线
手工运行
```

开发阶段验收：

```text
使用 Fake/Mock Gateway 验证发布、上线和手工运行流程，不启动 DolphinScheduler。
```

最终联调验收：

```text
不进入 DolphinScheduler UI
平台发布后 DS 中存在对应定义
平台点击运行后 DS 实际执行（最终验收阶段）
```

---

## Phase 7：调度

完成：

```text
调度配置UI
Cron
上线
下线
工作流实例
补数据
```

---

## Phase 8：运维

完成：

```text
任务实例
失败任务
日志
停止
重跑
```

---

## Phase 9：数据血缘

完成：

```text
SQL AST
source
target
lineage table
AntV X6
上下游查询
```

---

## Phase 10：权限/审计/完善

完成：

```text
用户
角色
项目权限
数据源权限
SQL审计
操作日志
告警渠道
```

---

# 31. 不要做的事情

开发阶段明确禁止：

```text
不要拆微服务
不要引入 Kafka
不要引入 Redis
不要引入 Kubernetes
不要自己实现调度内核
不要直接修改 DolphinScheduler 数据库
不要让前端直接调 DolphinScheduler
不要在 Controller 中拼 SeaTunnel 配置
不要在业务层散落 DS API DTO
不要让已发布任务直接引用开发态 content
不要在 Phase 0 至 Phase 10 期间启动 DolphinScheduler 或 SeaTunnel
```

---

# 32. 最终验收链路

必须完整跑通：

```text
创建 StarRocks 数据源
        ↓
数据探查
        ↓
查看元数据
        ↓
编写 SQL
        ↓
运行 SQL
        ↓
保存为开发文件
        ↓
创建离线同步任务
        ↓
SeaTunnel MySQL → StarRocks
        ↓
创建工作流
        ↓
拖入 SQL / Python / Shell / SeaTunnel
        ↓
保存
        ↓
发布
        ↓
生成版本快照
        ↓
提交 DolphinScheduler（最终验收阶段启动服务后）
        ↓
上线调度
        ↓
产生实例
        ↓
运维页面查看日志
        ↓
失败重跑 / 停止
        ↓
从 SQL 生成数据血缘
```

达到这条链路，第一版平台才算完成。
