#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${DATASPHERE_HOME:-$(cd "$SCRIPT_DIR/.." && pwd)}"
RUN_DIR="${DATASPHERE_RUN_DIR:-$APP_HOME/.run}"
DEFAULT_ENV_FILE="$RUN_DIR/datasphere.env"
if [[ -f "$APP_HOME/config/datasphere.env" ]]; then DEFAULT_ENV_FILE="$APP_HOME/config/datasphere.env"; fi
ENV_FILE="${DATASPHERE_ENV_FILE:-$DEFAULT_ENV_FILE}"
mkdir -p "$RUN_DIR"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

JAR="${DATASPHERE_JAR:-}"
if [[ -z "$JAR" ]]; then
  for candidate in "$APP_HOME/datasphere-0.1.0-SNAPSHOT.jar" "$APP_HOME/target/datasphere-0.1.0-SNAPSHOT.jar"; do
    if [[ -f "$candidate" ]]; then JAR="$candidate"; break; fi
  done
fi
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
  echo "DataSphere jar not found. Set DATASPHERE_JAR or place datasphere-0.1.0-SNAPSHOT.jar in $APP_HOME" >&2
  exit 1
fi

PID_FILE="$RUN_DIR/datasphere.pid"
LOG_FILE="${DATASPHERE_LOG:-$RUN_DIR/datasphere.log}"
RUNTIME_DIR="$RUN_DIR/runtime"
HASH_FILE="$RUN_DIR/jar.sha256"
if [[ -f "$PID_FILE" ]]; then
  PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ "$PID" =~ ^[0-9]+$ ]] && kill -0 "$PID" 2>/dev/null; then
    echo "DataSphere is already running, pid=$PID"
    exit 0
  fi
fi

CURRENT_HASH="$(sha256sum "$JAR" | awk '{print $1}')"
SAVED_HASH="$(cat "$HASH_FILE" 2>/dev/null || true)"
if [[ "$CURRENT_HASH" != "$SAVED_HASH" || ! -d "$RUNTIME_DIR/BOOT-INF/classes" ]]; then
  rm -rf "$RUNTIME_DIR"
  mkdir -p "$RUNTIME_DIR"
  (cd "$RUNTIME_DIR" && jar xf "$JAR")
  printf '%s' "$CURRENT_HASH" > "$HASH_FILE"
fi

CLASSPATH="$RUNTIME_DIR/BOOT-INF/classes:$RUNTIME_DIR/BOOT-INF/lib/*"
: > "$LOG_FILE"
nohup java ${JAVA_OPTS:-} -cp "$CLASSPATH" com.company.platform.DataSphere > "$LOG_FILE" 2>&1 &
PID=$!
printf '%s' "$PID" > "$PID_FILE"

PORT="${DATASPHERE_PORT:-8080}"
STARTED=false
for _ in {1..90}; do
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "DataSphere failed to start. See $LOG_FILE" >&2
    tail -n 120 "$LOG_FILE" >&2 || true
    rm -f "$PID_FILE"
    exit 1
  fi
  if grep -q "Started DataSphere" "$LOG_FILE" 2>/dev/null; then STARTED=true; fi
  if [[ "$STARTED" == "true" ]] && curl -fsS "http://127.0.0.1:${PORT}/api/health" >/dev/null 2>&1; then
    sleep 2
    if kill -0 "$PID" 2>/dev/null; then
      echo "DataSphere started, pid=$PID, log=$LOG_FILE"
      exit 0
    fi
  fi
  sleep 1
done

echo "DataSphere startup timed out or health check failed. See $LOG_FILE" >&2
tail -n 120 "$LOG_FILE" >&2 || true
exit 1
