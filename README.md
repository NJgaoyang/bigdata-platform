# Big Data Platform Execution Package

本执行包用于将 `bigdata-dev-platform-prototype.html` 的产品方向真正实现成可运行的大数据开发平台。

## 内容

- `IMPLEMENTATION_PLAN.md`
  - 完整技术方案
  - 数据库设计
  - API
  - 分阶段执行计划
  - Java / Vue / SQL / DolphinScheduler / SeaTunnel 伪代码

- `WORK_MODE_MASTER_PROMPT.md`
  - 可直接复制到 ChatGPT Work 模式中
  - 用于驱动 GPT-5.6 Luna 按阶段真实创建项目、编译、测试和修复

- `application-dev.example.yml`
  - 本地开发环境配置示例

- `bootstrap.sql`
  - 创建平台 MySQL 数据库的初始化脚本

## 建议使用方式

1. 新建一个空目录作为项目工作区。
2. 打开 ChatGPT Work。
3. 将本 ZIP 解压后的文件放入工作区，或者先将 `WORK_MODE_MASTER_PROMPT.md` 全文粘贴进去。
4. 要求模型严格从 Phase 0 开始执行。
5. 不要让模型跳过 `mvn clean test` / `npm run build`。
6. 每完成一个 Phase 后检查 `docs/PROGRESS.md`。
7. DolphinScheduler 已固定为 3.1.9，安装目录为 `/data/software/dolphinscheduler`，账号为 `admin`。
8. SeaTunnel 已固定为 2.3.12，安装目录为 `/data/software/seatunnel`。
9. Phase 0 至 Phase 10 开发期间不要启动 DolphinScheduler 或 SeaTunnel；先使用 Mock/Stub 完成开发、编译和测试，最终验收阶段再启动服务并执行真实联调。

## 开发数据库

```text
Host: 81.69.15.136
Port: 3306
Username: root
Password: 通过 `PLATFORM_DB_PASSWORD` 环境变量传入
Database: bigdata_platform
```

开发阶段可以直接使用。
生产环境不要把密码提交到 Git。

## 本地启动

默认配置使用 H2 内存库和 Mock 执行网关，不会启动 DolphinScheduler 或 SeaTunnel：

```bash
mvn spring-boot:run
cd frontend && npm run dev
```

访问 `http://localhost:5173`。如果需要连接开发 MySQL，启动时指定 `application-dev.example.yml` 对应配置；DolphinScheduler 与 SeaTunnel 仍保持关闭。

平台核心项目、文件、数据源和审计记录会在配置了 JDBC 数据库时同步到平台库；默认 H2 便于离线开发，切换到开发 MySQL 后由 Flyway 自动维护表结构。

### 可选的平台登录认证

开发环境默认关闭认证，便于直接访问 `http://localhost:5173`。正式环境可通过环境变量开启平台登录：

```bash
PLATFORM_AUTH_ENABLED=true
PLATFORM_ADMIN_USERNAME=admin
PLATFORM_ADMIN_PASSWORD_SHA256=管理员密码的SHA-256十六进制摘要
```

开启后，平台登录页会签发 Bearer 会话令牌，所有 `/api` 业务接口（健康检查和认证接口除外）都会校验令牌。密码不写入平台库；用户表已预留 `password_hash` 字段，后续可扩展为多用户密码管理。

### 多表同步

数据集成任务支持一张或多张源表分别映射到目标表。编辑任务时可通过“添加表”增加映射，在任务详情中删除单张表；删除后平台只重生成该任务的 SeaTunnel 配置，不会影响同任务中的其他表，历史单表任务也会自动兼容。

最终联调前，将 `platform.scheduler.dolphinscheduler.real-enabled` 改为 `true`，确认 API Token 和服务地址后，再启动外部服务。

## 查看真实执行记录

外部 DolphinScheduler 已运行时，本地后端需要使用真实模式启动，密码只通过环境变量传入，不写入项目文件：

```bash
DOLPHINSCHEDULER_REAL_ENABLED=true \
DOLPHINSCHEDULER_BASE_URL=http://81.69.15.136:12345 \
DOLPHINSCHEDULER_PROJECT_CODE=22919517565792 \
DOLPHINSCHEDULER_TENANT_CODE=bigdata \
DOLPHINSCHEDULER_PASSWORD='请填写实际密码' \
PLATFORM_SEATUNNEL_REAL_ENABLED=true \
DATASOURCE_MASTER_KEY='请使用密钥管理系统注入的随机主密钥' \
SPRING_DATASOURCE_URL='jdbc:mysql://81.69.15.136:3306/bigdata_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true' \
SPRING_DATASOURCE_USERNAME=root \
SPRING_DATASOURCE_PASSWORD='请填写平台库密码' \
mvn spring-boot:run
```

然后打开 `http://localhost:5173/operations`，或点击顶部“运维中心”，即可查看流程实例、任务实例和日志。页面右上角会显示当前是“真实调度器”还是“开发 Mock”模式。
# bigdata-platform
