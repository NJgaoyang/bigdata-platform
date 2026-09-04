# GPT Work 模式执行主提示词
# 目标模型：GPT-5.6 Luna
# 使用方式：在 Work 模式中新建工作区，将本文件全文作为第一条任务提示词粘贴执行。

你现在是本项目的首席架构师、Java 21 高级工程师、Vue 3 高级工程师、数据平台工程师和测试负责人。

你的任务不是只输出架构说明，而是要在当前 Work 工作区中**实际创建、修改、运行、检查和持续完善完整工程**，最终交付一个可运行的大数据开发平台。

==================================================
一、目标系统
==================================================

实现一个“大数据开发平台”，顶部一级模块：

1. 数据集成
2. 数据开发
3. 数据探查
4. 数据血缘
5. 调度中心
6. 运维中心
7. 系统配置

平台 UI、开发、工作流、调度配置和运维页面全部自行实现。

DolphinScheduler 仅作为底层调度和 DAG 执行引擎：
- 不使用 DolphinScheduler 自带 UI
- 前端不得直接调用 DolphinScheduler
- 不允许直接修改 DolphinScheduler 数据库
- 后端统一通过 SchedulerGateway / DolphinSchedulerGateway 调用 DolphinScheduler API

SeaTunnel 2.3.12 作为离线同步引擎。

==================================================
二、固定技术栈
==================================================

后端：
- Java 21
- Maven
- Spring Boot
- Spring Web
- MyBatis 或 MyBatis-Plus
- HikariCP
- Alibaba Druid SQL Parser
- Flyway
- SpringDoc OpenAPI
- Jackson
- SLF4J + Logback

前端：
- Vue 3
- TypeScript
- Vite
- Element Plus
- Monaco Editor
- Pinia
- Vue Router
- Axios
- AntV X6

数据与执行：
- 系统元数据：MySQL 8
- MySQL host：192.168.113.135
- username：root
- password：123456
- platform database：bigdata_platform
- StarRocks：3.3.22
- SeaTunnel：2.3.12，安装目录：/data/software/seatunnel
- DolphinScheduler：3.1.9，安装目录：/data/software/dolphinscheduler，账号：admin

禁止第一期主动加入：
- Redis
- Kafka
- Kubernetes
- 微服务
- Elasticsearch

必须采用 Spring Boot 单体 + Vue 3 前后端分离架构。

==================================================
二点五、服务启动顺序（必须遵守）
==================================================

开发阶段禁止启动 DolphinScheduler 和 SeaTunnel。

Phase 0 至 Phase 10 期间：
- 先完成项目代码、Mock/Stub、单元测试、编译和前端页面验证
- DolphinSchedulerGateway 和 SeaTunnelGateway 先使用 Fake/Mock 验证业务流程
- 不执行真实 DolphinScheduler 发布、调度和实例联调
- 不执行真实 SeaTunnel 同步任务
- 不运行 `/data/software/dolphinscheduler` 下的启动脚本
- 不运行 `/data/software/seatunnel` 下的启动脚本

只有全部代码开发完成、Phase 0 至 Phase 10 构建测试通过后，才可以在最终验收阶段启动 DolphinScheduler 3.1.9 和 SeaTunnel 2.3.12，再进行真实服务健康检查、API 联调和 MySQL → StarRocks 任务测试。

==================================================
三、最重要的架构边界
==================================================

必须创建：

SchedulerGateway
DolphinSchedulerGatewayImpl

SeaTunnelGateway
SeaTunnelGatewayImpl

业务代码禁止直接依赖 DolphinScheduler API DTO。

工作流：
- 保存：仅保存平台 MySQL
- 发布：才生成发布快照，并提交 DolphinScheduler
- 开发态和生产态必须隔离
- 已发布任务必须绑定 dev_file_version
- 修改 dev_file.content 不得影响已发布任务

离线同步：
- UI 配置 → IntegrationTask
- IntegrationTask → SeaTunnel Config
- SeaTunnel Config → 由 DolphinScheduler 节点触发执行
- 如果环境无 SeaTunnel Task Plugin，则使用 DolphinScheduler Shell Task 调 seatunnel.sh
- 不能因此自己重新写调度器

==================================================
四、UI要求
==================================================

整体风格参考企业级数据开发 IDE。

顶部一级导航：

数据集成 | 数据开发 | 数据探查 | 数据血缘 | 调度中心 | 运维中心 | 系统配置

当前模块：
- 蓝色文字
- 底部蓝色横线

不要使用大面积左侧主菜单。

数据开发页面：
左侧资源树，右侧 Monaco Editor。

资源树支持：
- 项目增删改查
- 文件夹增删改查
- SQL文件
- Shell文件
- Python文件
- 搜索
- 文件夹存在文件或子文件夹时禁止删除

Monaco：
- SQL/Python/Shell 高亮
- 多 Tab
- Ctrl+S
- Dirty 标识
- 代码补全
- 格式化
- SQL选中运行
- 全部运行
- 运行停止

数据探查：
左：数据库 / schema / table 元数据树
中：Monaco SQL Editor
右：表字段 + 类型 + 中文注释
下：ResultSet

双击表：
- 展示列名
- 类型
- 是否可空
- 中文注释

SQL：
- 运行
- 停止
- 选中部分运行
- ResultSet
- Error Message
- elapsed time

数据血缘：
- AntV X6
- 表级血缘
- 上游 / 当前 / 下游
- 节点可展开

调度：
- 工作流 AntV X6
- SQL节点
- Python节点
- Shell节点
- SeaTunnel节点
- 调度配置
- 工作流实例
- 补数据

运维：
- 任务实例
- 失败任务
- 日志
- 停止
- 重跑

实时同步：
featureFlag=false
默认不显示。

==================================================
五、工程目录
==================================================

创建：

bigdata-platform/
├── pom.xml
├── README.md
├── frontend/
└── src/main/java/...

建议 package：

com.company.bigdataplatform

后端模块目录：

common
config
system
datasource
development
metadata
query
integration
workflow
scheduler
operation
lineage

不得将所有类放在 controller/service 两个大目录中。

==================================================
六、数据库
==================================================

首先创建 Flyway migration。

至少实现：

data_source
dev_project
dev_folder
dev_file
dev_file_version
query_execution
integration_task
integration_instance
workflow
workflow_node
workflow_edge
schedule_config
data_lineage

必要时增加：
publish_record
operation_log
feature_flag

MySQL：

jdbc:mysql://192.168.113.135:3306/bigdata_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true

root / 123456

开发环境可以使用上述配置。

但 README 明确：
正式环境通过：
DB_URL
DB_USERNAME
DB_PASSWORD
读取，不允许生产密码写入 Git。

==================================================
七、执行方式
==================================================

必须分阶段执行。

不要一次生成一大堆未经验证的代码。

每完成一阶段：

1. 编译后端：
   mvn clean test
   mvn clean package

2. 编译前端：
   npm install
   npm run build

3. 如果可以运行：
   启动 Spring Boot
   启动前端
   实际打开页面检查

4. 发现错误必须先修复
5. 当前阶段通过后才能进入下一阶段
6. 将阶段结果记录到 docs/PROGRESS.md

如果 Work 模式提供浏览器能力：
- 必须实际打开页面
- 检查布局
- 检查 Console
- 检查 Network
- 至少验证关键交互
- 不能只根据源代码宣称页面正确

==================================================
八、执行阶段
==================================================

Phase 0：
- 初始化 Maven
- Spring Boot
- Vue 3
- TypeScript
- Element Plus
- MainLayout
- MySQL
- Flyway
- Result<T>
- Exception Handler
- Swagger

Phase 1：
数据源
- CRUD
- MySQL
- StarRocks
- test connection
- DynamicDataSourceManager
- password encryption

Phase 2：
数据开发
- 项目
- 文件夹
- 文件
- Resource Tree
- Monaco
- SQL
- Python
- Shell
- 多Tab
- Ctrl+S
- 版本管理
- 非空文件夹禁止删除

Phase 3：
数据探查
- information_schema
- databases
- tables
- columns
- comments
- SQL execute
- selected SQL execute
- cancel
- result grid

Phase 4：
SeaTunnel 2.3.12（开发阶段不启动服务）
- integration task CRUD
- UI config model
- SeaTunnel config builder
- MySQL → StarRocks
- validation
- execute（开发阶段使用 Mock/Stub；真实执行延后到最终验收）
- status
- log

Phase 5：
工作流
- AntV X6
- workflow
- node
- edge
- SQL node
- Python node
- Shell node
- SeaTunnel node
- DAG validation

Phase 6：
发布（开发阶段不启动 DolphinScheduler）
- dev_file_version snapshot
- workflow publish snapshot
- SchedulerGateway
- DS converter
- DS task definitions
- DS process definition
- publish
- online
- manual run（开发阶段使用 Mock/Stub）

Phase 7：
调度
- schedule UI
- cron
- online/offline
- process instances
- backfill

Phase 8：
运维
- task instances
- failed tasks
- logs
- stop
- rerun

Phase 9：
血缘
- Druid AST
- source table
- target table
- data_lineage
- X6 graph

Phase 10：
- role
- permission
- project permission
- feature flag
- operation log
- alerts
- polish

==================================================
九、编码规范
==================================================

Java：
- 使用 record 作为适合的 Request/Response DTO
- Entity 不直接返回前端
- Controller 不写业务逻辑
- Service 不直接拼 DolphinScheduler HTTP JSON
- 使用 Gateway 隔离外部系统
- 所有数据库写操作必须考虑事务
- 所有状态使用 enum
- 时间统一使用 LocalDateTime
- JSON 配置封装成明确 DTO，不到处 Map<String,Object>
- 重要流程写单元测试
- 禁止空 catch
- 禁止 System.out.println

前端：
- Vue Composition API
- TypeScript strict
- API 按模块拆分
- Pinia 管编辑器/项目/工作流状态
- Monaco Editor 独立组件
- AntV X6 独立组件
- 禁止把整个平台堆进单个 Vue 文件
- Element Plus 统一样式
- 重要异步操作有 loading
- 失败有 ElMessage
- 删除有二次确认

==================================================
十、SQL查询实现要求
==================================================

业务SQL必须 JDBC 执行，不使用 MyBatis。

必须实现：

RunningStatementRegistry

核心逻辑：

queryId -> java.sql.Statement

停止：

statement.cancel()

执行接口：

POST /api/query/execute

取消：

POST /api/query/{queryId}/cancel

如果用户在 Monaco 中选择了 SQL：
只提交选中内容。

否则提交全部内容。

默认 maxRows=1000。

防止一次返回几十万行导致浏览器卡死。

==================================================
十一、SQL安全
==================================================

使用 Druid Parser。

至少识别：

DROP DATABASE
DROP TABLE
TRUNCATE
DELETE 无 WHERE
UPDATE 无 WHERE

危险 SQL：
默认拦截。

管理员可经过二次确认执行时：
必须写 operation_log。

==================================================
十二、DolphinScheduler
==================================================

精确 DS 版本通过配置：

platform.scheduler.dolphinscheduler.base-url
platform.scheduler.dolphinscheduler.version
platform.scheduler.dolphinscheduler.install-dir
platform.scheduler.dolphinscheduler.username
platform.scheduler.dolphinscheduler.token

先实现接口和 Mock/Stub。

只有最终验收阶段启动 DS 后，才做真实 API Adapter 联调；开发阶段继续使用 Mock/Stub。

严禁：
- 直接 update DolphinScheduler DB
- 前端调 DS API
- 用 DS UI 代替平台 UI

==================================================
十三、SeaTunnel
==================================================

固定 2.3.12。

实现：

IntegrationTask
SeaTunnelConfigBuilder
SeaTunnelGateway

优先实现：

MySQL → StarRocks

后续再扩展：

StarRocks → StarRocks
MySQL → MySQL

生成的配置应该能持久化到 integration_task.seatunnel_config。

密码渲染到执行配置时注意不要在普通日志中打印明文。

==================================================
十四、必须实现的核心伪代码
==================================================

1. 删除文件夹：

if folder has child folder:
    reject

if folder has file:
    reject

delete folder

2. SQL执行：

create queryId
get dynamic jdbc connection
create statement
register queryId -> statement
execute
convert result
finally remove statement and close

3. SQL取消：

statement = registry.get(queryId)
statement.cancel()

4. 发布：

load workflow
load nodes
load edges
validate DAG
snapshot file versions
build published workflow
convert tasks
convert process
SchedulerGateway.publish
save ds codes
mark published

5. 血缘：

parse SQL AST
collect source tables
collect target tables
replace lineage records for file version

==================================================
十五、测试
==================================================

至少包含：

FolderServiceTest
FileVersionServiceTest
DagValidatorTest
SqlSafetyCheckerTest
SqlLineageParserTest
WorkflowPublishServiceTest
SeaTunnelConfigBuilderTest

Gateway 使用 Fake / Mock 测试。

集成测试至少验证：

MySQL repository
dynamic datasource
metadata query

如果 StarRocks 可访问：
做 StarRocks smoke test。

==================================================
十六、验收标准
==================================================

完成后必须满足：

1.
数据源可配置 MySQL 和 StarRocks。

2.
数据开发：
可以新增：
- 项目
- 文件夹
- SQL
- Python
- Shell

3.
文件夹非空时不能删除。

4.
点击文件右侧 Monaco 打开对应代码。

5.
SQL 支持关键字高亮和代码补全。

6.
数据探查左侧展示 StarRocks/MySQL 元数据。

7.
双击表显示字段与中文注释。

8.
SQL 支持：
- 运行全部
- 选中运行
- 停止
- 结果集

9.
SeaTunnel 2.3.12 至少完成 MySQL → StarRocks。

10.
工作流 UI 全部由平台实现。

11.
工作流保存不提交 DS。

12.
工作流发布才提交 DS。

13.
修改开发文件不影响已发布版本。

14.
平台能查看 DS 工作流实例。

15.
平台能查看任务日志。

16.
平台可停止 / 重跑。

17.
可从 SQL 解析表级血缘。

18.
实时同步默认不显示。

19.
前后端均可成功 build。

20.
README 包含完整启动说明。

==================================================
十七、执行纪律
==================================================

从 Phase 0 开始立即执行。严格遵守“先开发、最后启动外部服务”的顺序，当前不要启动 DolphinScheduler 或 SeaTunnel。

不要只回复：
“方案如下”
“建议如下”
“可以实现”

而是实际在 Work 工作区中：
- 创建文件
- 修改代码
- 执行命令
- 查看错误
- 修复
- 验证

每阶段结束向我报告：

【本阶段完成】
【新增文件】
【关键实现】
【测试结果】
【当前可运行功能】
【下一阶段】

如果遇到与当前版本相关的不确定信息：
优先查项目本地依赖、官方文档或实际 API，
不要凭印象硬编码。

从 Phase 0 开始。
