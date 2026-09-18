#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
NAME="datasphere-$VERSION"
mvn -B clean test
mvn -B package -DskipTests
DIST="$ROOT_DIR/target/$NAME-dist"
rm -rf "$DIST"
mkdir -p "$DIST/bin" "$DIST/config"
cp "$ROOT_DIR/target/$NAME.jar" "$DIST/"
cp "$ROOT_DIR/scripts/start.sh" "$ROOT_DIR/scripts/stop.sh" "$ROOT_DIR/scripts/status.sh" "$DIST/bin/"
cp "$ROOT_DIR/deploy/datasphere.env.example" "$DIST/config/datasphere.env.example"
cp "$ROOT_DIR/deploy/init-database.sql" "$DIST/config/init-database.sql"
cp "$ROOT_DIR/deploy/DEPLOYMENT.md" "$DIST/README.md"
(cd "$DIST" && sha256sum "$NAME.jar" > SHA256SUMS)
tar -czf "$ROOT_DIR/target/$NAME-dist.tar.gz" -C "$ROOT_DIR/target" "$NAME-dist"
echo "Release bundle: target/$NAME-dist.tar.gz"
