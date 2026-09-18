# DataSphere deployment

DataSphere is distributed as `datasphere-0.1.0-SNAPSHOT.jar`. The metadata database is a dedicated MySQL database named `datasphere`; Flyway creates and upgrades all tables automatically.

## Requirements

- JDK 21 (the bundled scripts use `java`, `jar` and `jps`)
- MySQL 8.0
- Node.js/npm are only needed when building from source, not on the deployment host
- SeaTunnel / DolphinScheduler are configured externally when those runtime modes are enabled

## Prepare MySQL

From a source checkout, run `deploy/init-database.sql`. From a release bundle, run `config/init-database.sql`. Then create a dedicated MySQL account with privileges on `datasphere.*`. Do not reuse the old `bigdata_platform` database for a new DataSphere installation.

## Configure

Copy `deploy/datasphere.env.example` to `.run/datasphere.env` when running from source, or copy `config/datasphere.env.example` to `config/datasphere.env` in a release bundle. Fill in the target environment values. Secrets must not be committed to Git.

For the first production start, set `DATASPHERE_ADMIN_INITIAL_PASSWORD` to the initial admin password. DataSphere hashes it with PBKDF2 and stores only the hash in MySQL. After the first successful start, remove `DATASPHERE_ADMIN_INITIAL_PASSWORD` from the environment file.

## Start from a release bundle

Place `datasphere-0.1.0-SNAPSHOT.jar` in the release root and run `bin/start.sh`. Use `bin/status.sh` and `bin/stop.sh` for lifecycle management. The launcher expands the executable jar into a private runtime directory and starts `com.company.platform.DataSphere`, so normal `jps` output is `DataSphere`.

## Build a portable release bundle

Run `scripts/build-release.sh`. It produces `target/datasphere-0.1.0-SNAPSHOT-dist.tar.gz`, containing the jar, lifecycle scripts, configuration template, database initialization SQL, SHA-256 checksum and this deployment guide. Extract that archive on another JDK 21 server, configure the new environment, and start it without rebuilding the frontend or backend.
