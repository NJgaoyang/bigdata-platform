# DataSphere Production Deployment

DataSphere v11 is deployed as an independent application and uses its own `datasphere` metadata database.

Use the portable deployment guide at [`deploy/DEPLOYMENT.md`](../deploy/DEPLOYMENT.md).

The supported deployment artifacts are:

- `datasphere-0.1.0-SNAPSHOT.jar`
- `scripts/start.sh`, `scripts/stop.sh`, `scripts/status.sh`
- `config/datasphere.env` or `.run/datasphere.env`

Do not use the old `PLATFORM_*` environment variables or the `bigdata_platform` metadata database.
