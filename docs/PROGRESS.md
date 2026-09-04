# 实施进度

## 当前状态

- 已完成：Phase 0 工程骨架、Flyway 元数据迁移、基础 API、前端工作台布局、Mock/Stub 执行网关。
- 已完成：数据源、数据开发、数据探查、离线同步、工作流、发布、调度运维、血缘和系统配置的第一版可运行功能。
- 已推进：数据源连接测试、动态 Hikari JDBC 连接池、MySQL/StarRocks JDBC 元数据读取、真实 JDBC 查询入口（无数据源时保留 Mock 回退）。
- 已推进：Phase 7 调度配置闭环，支持 Cron、时区、失败策略、上线、下线和补数据，并通过 Mock SchedulerGateway 验证。
- 已推进：数据探查和系统配置页面接入数据源/元数据 API；数据源页面支持新增、保存和连接测试，数据探查只加载真实 StarRocks 数据库、表和字段，无法连接时显示空状态。
- 已推进：调度中心支持调度配置、Cron 校验、上线/下线和补数据接口；已用本地 Mock API 跑通“创建工作流 → 发布 → 保存 Cron → 上线”链路。
- 已完成：工作流 X6 DAG 画布、版本快照与发布前文件版本校验；发布 SQL 版本会自动生成血缘。
- 已完成：X6 画布节点编码到后端节点 ID 的映射，支持新增/编辑工作流连线保存，并加入回归测试。
- 已完成：离线同步任务及 SeaTunnel 执行实例的元数据持久化，页面运行/日志操作已接入网关。
- 已完成：Phase 10 用户、角色、角色权限、项目权限、数据源权限、告警渠道和 SQL/操作审计接口，并同步平台数据库。
- 当前默认模式：`development-mock`；真实 DolphinScheduler/SeaTunnel 适配器通过显式开关启用，默认不触达外部服务。
- DolphinScheduler：3.1.9，`/data/software/dolphinscheduler`；已完成远程健康检查、admin 登录、sessionId 认证、项目与租户创建、流程定义接口联调。
- DolphinScheduler 发布闭环：平台会将 X6 节点、节点参数、连线和坐标转换为 3.1.9 流程定义，支持 `SEATUNNEL`、`SHELL`、`PYTHON`、`SQL` 节点，并调用上线/下线接口；DS 流程编码已持久化，平台重启后仍可运行已发布流程。
- DolphinScheduler 运行兼容：3.1.9 的启动接口只返回命令提交成功，不直接返回实例号；平台会按流程编码回查实例，回查不到时返回 `SUBMITTED` 并提示等待 Master，避免伪造实例状态。远程 Master 消费命令后，平台已核验真实流程实例、任务实例和任务日志。
- SeaTunnel：`/data/software/seatunnel`；已完成 MySQL 到 StarRocks 的真实 JDBC 与 Stream Load 同步验证。
- 已补齐：平台运行环境已创建并迁移远程 MySQL `bigdata_platform` 元数据库；本地 8080 实例当前使用 MySQL 持久化，而不是临时 H2。
- 已补齐：已登记并验证 MySQL `yzl_prd` 与 StarRocks `ods` 数据源，元数据树和查询接口均可读取 `yzl_order`，两端查询结果各返回 1,000 行。
- 已补齐：数据开发页面改用真实 Monaco Editor，支持 SQL/Python/Shell 语言模式、选中执行、Ctrl/Cmd+S 和代码补全；文件首次保存会落入平台项目并生成版本。
- 已补齐：数据集成页面接入 SeaTunnel 实例状态轮询和可查看日志，运维页面接入失败任务筛选、类型/状态筛选和任务日志。
- 已补齐：系统配置页面支持数据源编辑/删除，并可查看用户、角色、审计、告警渠道和操作日志接口返回的数据。
- 本轮继续补齐：数据开发资源树读取平台项目、文件夹和文件，资源新建、重命名、删除、保存均调用后端落库；无后端数据时显示空状态，不注入原型资源。
- 本轮继续补齐：数据探查编辑器统一为 Monaco，保留选中 SQL 执行、格式化、停止、真实 EXPLAIN、字段元数据和结果集功能；不再注入静态表结构和静态查询结果。
- 本轮继续补齐：离线同步任务支持详情编辑和更新，结构化来源/目标配置中的密码采用 AES-GCM 加密；返回前端的配置和 SeaTunnel HOCON 均只显示脱敏值，空密码更新会保留原密码。
- 本轮继续补齐：已有 DolphinScheduler 流程发布时使用 PUT 更新，首次发布才 POST 创建；调度配置持久化 worker group、alert group、ds schedule id 和并行度。
- 本轮继续补齐：新增持久化查询历史接口 `/api/query/history`，数据探查页面可查看最近 100 条查询的时间、数据源、状态、耗时、SQL 和错误信息；重启后仍可读取。
- 本轮继续补齐：数据开发页面“历史”入口也接入同一查询历史接口，数据开发与数据探查均可查看持久化执行记录。
- 本轮继续补齐：新增 `.github/workflows/ci.yml`，Pull Request 和主干提交自动执行 Java 21 后端测试及 Node 20 前端构建。

## 验证记录

- `mvn clean test`：通过，10 个测试通过。
- `mvn clean package`：通过。
- `npm install`：通过。
- `npm run build`：通过。
- `mvn clean package`：通过（包含 JDBC 元数据与调度配置改动）。
- `mvn clean test`：通过，18 个测试通过（包含 DS 节点/连线转换回归测试）。
- `mvn clean package`：通过。
- `npm run build`：通过（仅有 Monaco 打包体积提示）。
- 运维页面已接入实例列表、日志、重跑 API；真实 DS 无实例时页面显示空列表，不再显示演示实例。
- 本地 API 冒烟验证：工作流发布、调度配置保存、调度上线均返回成功。
- 本地 API 冒烟验证：X6 画布更新、节点编码连线解析和 DAG 校验均返回成功。
- 本地 API 冒烟验证：Mock SQL 查询、离线同步任务创建/运行/日志查询均返回成功。
- 外部服务检查：DolphinScheduler master/worker/alert/api 与 SeaTunnel master/worker 均处于运行状态；未执行停止或重启。
- 真实联调验证：远程 DolphinScheduler 3.1.9 已创建项目 `bigdata-platform`、`bigdata` 租户及流程定义；项目编码已写入 `application-dev.example.yml`，后续已完成真实流程发布、上线和执行验收。
- 真实联调验证：SeaTunnel 2.3.12 `seatunnel.sh --help` 正常返回，确认命令行入口及 cluster 部署参数可用；未在缺少业务连接参数时启动数据同步。
- 真实联调验证：已从 MySQL `yzl_prd.yzl_order` 通过 SeaTunnel JDBC Source + StarRocks JDBC 兼容模式同步 1,000 条脱敏订单至 `ods.yzl_order`；作业状态为 `FINISHED`。目标 StarRocks 未开放 HTTP Stream Load 端口，因此配置生成器使用可用的 9030 JDBC 通道和 `compatible_mode = "StarRocks"`。
- 真实联调验证：已使用 SeaTunnel StarRocks Stream Load Sink 从 MySQL `yzl_prd.yzl_order` 同步至 `ods.yzl_order`；作业 `1148082292648837122` 状态为 `FINISHED`，源端 1,000 行、目标端 1,000 行且 `order_id` 去重后为 1,000。
- Stream Load 网络结论：8030 FE 入口可访问，但会重定向到 Docker 私网 `172.17.0.12:8040`，该地址不对 SeaTunnel Worker 可达；改用云服务器映射的 `81.69.15.136:8040` 后写入成功。可复用、无明文密码的任务配置位于 `examples/seatunnel/yzl-order-streamload.conf`，密码通过 SeaTunnel `-i` 参数注入。
- 真实 DS 发布验收：已发布并核验远程流程 `real-run-check-2`，状态 `ONLINE`，包含 1 个 `SHELL` 节点和 1 条根关系；平台上线接口返回成功，运行接口返回 `SUBMITTED`。
- 真实 DS 执行验收：远程流程 `real-execution-fix` 已创建 1 个真实 `SHELL` 节点并执行成功；平台回查到 DolphinScheduler 流程实例 `SUCCESS`，运维接口和日志接口均返回真实数据。
- 真实 DS 节点/连线验收：远程流程 `real-edge-check`（编码 `22919822640352`）已上线，包含 `step-one`、`step-two` 两个 `SHELL` 节点，关系为 1 条根关系加 1 条 `step-one → step-two` 连线；真实流程实例、两个任务实例均为 `SUCCESS`，日志中分别包含 `step-one` 和 `step-two` 输出。
- 平台入口级 SeaTunnel 验收：通过 `/api/integration/tasks/{id}/run` 提交 MySQL → StarRocks 任务，真实 SeaTunnel 集群返回 `FINISHED`，读取 1,000 行、写入 1,000 行、失败数为 0；状态轮询和日志接口均已验证。
- 持久化回归修复：执行实例状态更新采用 upsert，平台重启后 ID 从元数据库最大值继续生成，避免重复主键；同步任务 API 响应中的 SeaTunnel 密码已统一脱敏。
- 远程调度器运行态验收：DolphinScheduler Master、Worker 心跳正常；未停止或重启 DolphinScheduler、SeaTunnel 服务。
- 本轮真实平台验收：远程 MySQL 平台库创建成功，Flyway v1-v6 迁移成功；MySQL/StarRocks 数据源连接测试、元数据读取和 SQL COUNT 查询均成功。
- 本轮架构修复：查询新增异步 submit/status/cancel 生命周期，前端不再等待同步 SQL 请求返回后才获得 queryId；RunningStatementRegistry 支持取消请求先于 JDBC Statement 注册。
- 本轮一致性修复：启动加载 `dev_file_version`、`data_lineage` 和调度并行度；文件删除同步清理版本和血缘；血缘写入 MySQL；编辑已发布工作流保留生产 DolphinScheduler 映射和已发布版本号。
- 本轮安全修复：数据源密码支持 AES-GCM 随机 IV，兼容历史密文；正式环境通过 `DATASOURCE_MASTER_KEY` 注入主密钥，编辑数据源密码留空保持原密码。
- 已补齐：数据探查入口仅展示 StarRocks 数据源；元数据读取、字段读取和 SQL 执行接口均在后端拒绝 MySQL 数据源，业务库只用于同步任务。
- 已补齐：调度配置改为“分 / 时 / 天 / 月 / 周”可读化设置，自动生成 Quartz Cron，并展示执行说明及预计下次执行时间前 5 次；保存后同步更新工作流列表。
- 本轮验证：前端 `npm run build`、后端 `mvn clean test -q` 均通过；本地后端真实模式已重载，StarRocks 查询返回 1,000 条统计结果，MySQL 探查请求返回 400 并提示仅允许 StarRocks。
- 本轮验证：查询历史接口返回 6 条持久化记录，最新 StarRocks COUNT 查询状态为 `SUCCESS`；前端 `npm run build` 与后端 `mvn test -q` 均通过。
- 本轮继续补齐：工作流页面按后端详情回填 X6 节点与连线，保存画布时保留节点绑定的文件版本；工作流列表新增真实的上线/下线操作。
- 本轮继续补齐：新增可开关的平台认证，支持登录页、SHA-256 管理员密码摘要、Bearer 会话、退出登录及全 `/api` 业务接口拦截；默认开发模式保持关闭。
- 本轮数据库验证：Flyway v6 已成功应用到远程 `bigdata_platform`，`platform_user.password_hash` 字段已创建；远程 DolphinScheduler、SeaTunnel 未停止或重启。
- 最终回归：`mvn clean package` 通过，21 个测试全部通过；`npm run build` 通过（仅保留 Monaco 体积提示）。
- 本轮同步模型升级：参考 `/root/datasync-server` 引入 `integration_task_table` 明细模型，支持单表/多表任务、每张表独立源库/目标库映射、任务详情查看和删除单表后自动重生成 SeaTunnel 配置；旧单表任务兼容读取。
- 本轮同步验收：临时双表任务成功生成 2 个输入与 2 个输出，删除其中 1 张后剩余配置仅包含另一张表；临时任务已清理，未触发真实 SeaTunnel 作业。
- 本轮前端真实性修复：集成、开发、工作流、探查、工作台页面移除静态示例记录和假成功操作；运行、日志、删除、EXPLAIN、统计和活动均改为真实接口或明确空状态/只读状态。
- 本轮系统设置升级：设置页改为用户管理、权限中心、数据源、审计日志四项左侧导航；用户支持搜索、创建、编辑、启用/禁用和删除，模块权限按用户单独保存，数据源继续作为 StarRocks 探查/开发入口。
- 本轮审计与访问控制：新增 `user_permission` 表；用户登录密码采用 SHA-256 摘要，登录、退出、接口访问、权限拒绝、用户/权限变更均记录审计日志；认证开启时后端按模块权限拦截接口，管理员保留全模块权限。
- 默认 Spring Boot 配置使用 H2 内存库；`application-dev.example.yml` 用于切换到开发 MySQL。

## 计划完成状态

- Phase 0 → Phase 10 的主要业务闭环已完成；本轮继续补齐资源树落库、探查 Monaco、同步任务安全编辑、多表同步及删除单表、DS 流程更新、调度运行配置持久化、查询历史、CI 守门、工作流详情回填和可开关登录认证。正式生产化仍需完成统一 Repository 重构、细粒度角色/项目权限拦截，以及将 SeaTunnel 作业完全改为 DolphinScheduler Worker 执行。
- 最终构建验证：Maven 测试与打包通过，前端生产构建通过。
