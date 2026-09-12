# Production deployment notes

This branch removes silent mock fallbacks from the production path and publishes development files to DolphinScheduler according to the bound file type.

## DolphinScheduler task mapping

| Development file type / extension | DolphinScheduler task type |
| --- | --- |
| `SQL`, `.sql` | `SQL` |
| `PY`, `PYTHON`, `.py` | `PYTHON` |
| `SH`, `SHELL`, `.sh`, `.bash` | `SHELL` |
| `SEATUNNEL`, `HOCON`, `CONF`, `.seatunnel`, `.hocon`, `.conf` | `SEATUNNEL` |

The type is derived from the bound development file at workflow publish time. The UI node type is not trusted as the source of truth.

For SQL tasks, `configJson` must contain the DolphinScheduler datasource id, for example:

```json
{
  "datasourceId": 12,
  "type": "MYSQL"
}
```

Invalid or missing `datasourceId` now stops publishing instead of creating an unusable task.

## Failure retry

Defaults are configured through environment variables:

```bash
DOLPHINSCHEDULER_FAIL_RETRY_TIMES=3
DOLPHINSCHEDULER_FAIL_RETRY_INTERVAL=1
DOLPHINSCHEDULER_WORKER_GROUP=default
```

A workflow node can override retry behavior in `configJson`:

```json
{
  "failRetryTimes": 5,
  "failRetryInterval": 2,
  "workerGroup": "default"
}
```

`failRetryTimes` is constrained to `0..10`; `failRetryInterval` is constrained to `1..60` minutes.

## Required production configuration

Run with the `prod` Spring profile and inject credentials through environment variables. Do not commit passwords to source control.

```bash
SPRING_PROFILES_ACTIVE=prod
PLATFORM_DB_URL=jdbc:mysql://<metadata-db-host>:3306/bigdata_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
PLATFORM_DB_USERNAME=<username>
PLATFORM_DB_PASSWORD=<password>
PLATFORM_AUTH_ENABLED=true
PLATFORM_ADMIN_PASSWORD_SHA256=<sha256>
DOLPHINSCHEDULER_BASE_URL=http://<dolphinscheduler-host>:12345
DOLPHINSCHEDULER_USERNAME=admin
DOLPHINSCHEDULER_PROJECT_CODE=<numeric-project-code>
DOLPHINSCHEDULER_TENANT_CODE=bigdata
DOLPHINSCHEDULER_PASSWORD=<password>
DOLPHINSCHEDULER_REAL_ENABLED=true
SEATUNNEL_REAL_ENABLED=true
```

A DolphinScheduler token may be supplied with `DOLPHINSCHEDULER_TOKEN` instead of a password.

## Business source and StarRocks ODS

The repository already contains a SeaTunnel batch example for copying `yzl_prd.yzl_order` into StarRocks `ods.yzl_order`. Keep source/target credentials external. Before enabling the production schedule, verify the business MySQL port and create the StarRocks ODS table from the real source schema; do not infer a production table schema from sample data.

## Startup behavior

With the `prod` profile the application fails startup when authentication, SeaTunnel real mode, DolphinScheduler real mode, scheduler credentials, project code, retry configuration, or worker group is invalid. This is intentional: production must not silently fall back to simulated execution.
