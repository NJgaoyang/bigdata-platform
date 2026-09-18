# DataSphere deployment

DataSphere is distributed as `datasphere-0.1.0-SNAPSHOT.jar`. The metadata database is a dedicated MySQL database named `datasphere`; Flyway creates and upgrades all tables automatically.

## Requirements

- JDK 21 (the bundled scripts use `java`, `jar` and `jps`)
- MySQL 8.0
- Node.js/npm are only needed when building from source, not on the deployment host
- SeaTunnel / DolphinScheduler are configured externally when those runtime modes are enabled

## Prepare MySQL

Run `deploy/init-database.sql` with a database administrator, then create a dedicated account with privileges on `datasphere.*`. Do not reuse the old `bigdata_platform` database for a new DataSphere installation.

## Configure

Copy `config/datasphere.env.example` to `.run/datasphere.env` (source checkout) or `config/datasphere.env` (release bundle) and fill in the target environment values. Secrets must not be committed to Git.

## Start from a release bundle

Place `datasphere-0.1.0-SNAPSHOT.jar` in the release root and run `bin/start.sh`. Use `bin/status.sh` and `bin/stop.sh` for lifecycle management. The launcher expands the executable jar into a private runtime directory and starts `com.company.platform.DataSphere`, so normal `jps` output is `DataSphere`.

## Build a portable release bundle

Run `scripts/build-release.sh`. It produces `target/datasphere-0.1.0-SNAPSHOT-dist.tar.gz`, containing the jar, lifecycle scripts, configuration template and this deployment guide. Extract that archive on another JDK 21 server, configure the new environment, and start it without rebuilding the frontend or backend.
