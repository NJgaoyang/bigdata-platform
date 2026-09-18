# DataSphere

DataSphere 是面向企业内部的数据开发与治理平台。v11 作为新的系统基线维护，不再依赖旧 `frontend`、旧 `bigdata_platform` 元数据库或旧的部署命名。

## 技术基线

- Backend: Java 21 + Spring Boot 3.4.5 + MySQL 8.0 + Flyway
- Frontend: Vue 3 + TypeScript + Vite（唯一前端目录：`frontend-v2`）
- Metadata database: `datasphere`
- Package: `datasphere-0.1.0-SNAPSHOT.jar`
- Main process: `DataSphere`

## 本地构建

```bash
mvn clean test
mvn package -DskipTests
```

Maven 在打包阶段会自动执行 `frontend-v2` 的 `npm ci` 与生产构建，并将前端静态资源打进 Spring Boot jar。

## 新环境部署

先在 MySQL 8.0 中执行：

```sql
CREATE DATABASE IF NOT EXISTS datasphere
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;
```

复制 `deploy/datasphere.env.example` 为运行环境配置，填写数据库、凭据主密钥和外部运行组件地址。详细步骤见 `deploy/DEPLOYMENT.md`。

源码目录可直接使用：

```bash
scripts/start.sh
scripts/status.sh
scripts/stop.sh
```

构建可搬迁发布包：

```bash
scripts/build-release.sh
```

产物为 `target/datasphere-0.1.0-SNAPSHOT-dist.tar.gz`。解压到另一台 JDK 21 服务器、修改环境配置后即可启动，无需重新构建前端或后端。
