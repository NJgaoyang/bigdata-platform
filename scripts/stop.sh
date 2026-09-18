#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${DATASPHERE_HOME:-$(cd "$SCRIPT_DIR/.." && pwd)}"
RUN_DIR="${DATASPHERE_RUN_DIR:-$APP_HOME/.run}"
PID_FILE="$RUN_DIR/datasphere.pid"
if [[ ! -f "$PID_FILE" ]]; then echo "DataSphere is not running"; exit 0; fi
PID="$(cat "$PID_FILE" 2>/dev/null || true)"
if [[ ! "$PID" =~ ^[0-9]+$ ]] || ! kill -0 "$PID" 2>/dev/null; then rm -f "$PID_FILE"; echo "DataSphere is not running"; exit 0; fi
kill "$PID"
for _ in {1..30}; do
  if ! kill -0 "$PID" 2>/dev/null; then rm -f "$PID_FILE"; echo "DataSphere stopped"; exit 0; fi
  sleep 1
done
kill -9 "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "DataSphere stopped (forced)"
