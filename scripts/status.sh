#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${DATASPHERE_HOME:-$(cd "$SCRIPT_DIR/.." && pwd)}"
RUN_DIR="${DATASPHERE_RUN_DIR:-$APP_HOME/.run}"
PID_FILE="$RUN_DIR/datasphere.pid"
PID="$(cat "$PID_FILE" 2>/dev/null || true)"
if [[ "$PID" =~ ^[0-9]+$ ]] && kill -0 "$PID" 2>/dev/null; then
  echo "DataSphere RUNNING pid=$PID"
  jps 2>/dev/null | grep -E "^${PID}[[:space:]]+DataSphere$" || true
  exit 0
fi
echo "DataSphere STOPPED"
exit 1
