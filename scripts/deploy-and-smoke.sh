#!/usr/bin/env bash
set -euo pipefail

BRANCH="${PLATFORM_BRANCH:-fix/dev202609101-production-ready}"
BASE_URL="${PLATFORM_BASE_URL:-http://127.0.0.1:8080}"
APP_JAR="${PLATFORM_JAR:-target/bigdata-platform-0.1.0-SNAPSHOT.jar}"
LOG_FILE="${PLATFORM_LOG:-platform.log}"
RUN_DIR="${PLATFORM_RUN_DIR:-.run}"
PID_FILE="$RUN_DIR/bigdata-platform.pid"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
mkdir -p "$RUN_DIR"

ENV_FILE="${PLATFORM_ENV_FILE:-${RUN_DIR}/platform.env}"
if [[ -f "$ENV_FILE" ]]; then
  echo "[deploy] loading runtime environment: $ENV_FILE"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

echo "[deploy] repository: $ROOT_DIR"
echo "[deploy] branch: $BRANCH"

retry_git() {
  local attempt
  for attempt in 1 2 3; do
    if git -c http.version=HTTP/1.1 "$@"; then
      return 0
    fi
    echo "[deploy] git $1 failed (attempt $attempt/3); retrying..."
    sleep $((attempt * 3))
  done
  echo "[deploy] git $1 failed after 3 attempts"
  return 1
}

retry_git fetch origin "$BRANCH"
git checkout "$BRANCH"
retry_git pull --ff-only origin "$BRANCH"
echo "[deploy] commit: $(git rev-parse --short HEAD) $(git log -1 --pretty=%s)"

echo "[test] running backend tests"
mvn -B clean test

echo "[package] building deployable jar (includes frontend npm ci/build)"
mvn -B package -DskipTests

test -f "$APP_JAR"
jar tf "$APP_JAR" | grep -q 'BOOT-INF/classes/static/index.html'
echo "[package] jar and packaged frontend verified"

is_app_java_pid() {
  local pid="$1"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  [[ "$(ps -p "$pid" -o comm= 2>/dev/null | xargs)" == "java" ]] || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -Fq -- "$(basename "$APP_JAR")"
}

find_app_pid() {
  local pid
  while read -r pid; do
    if is_app_java_pid "$pid"; then
      echo "$pid"
      return 0
    fi
  done < <(pgrep -f "$(basename "$APP_JAR")" || true)
  return 1
}

OLD_PID=""
if [[ -f "$PID_FILE" ]]; then
  CANDIDATE="$(cat "$PID_FILE" 2>/dev/null || true)"
  if is_app_java_pid "$CANDIDATE"; then OLD_PID="$CANDIDATE"; fi
fi
if [[ -z "$OLD_PID" ]]; then OLD_PID="$(find_app_pid || true)"; fi

if [[ -n "$OLD_PID" ]]; then
  echo "[restart] stopping java pid $OLD_PID"
  kill "$OLD_PID" || true
  for _ in {1..30}; do
    if ! kill -0 "$OLD_PID" 2>/dev/null; then break; fi
    sleep 1
  done
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "[restart] process did not stop gracefully; sending SIGKILL"
    kill -9 "$OLD_PID" || true
  fi
fi

REMAINING_PID="$(find_app_pid || true)"
if [[ -n "$REMAINING_PID" ]]; then
  echo "[restart] ERROR: old application java process is still running: $REMAINING_PID"
  exit 1
fi

: > "$LOG_FILE"
echo "[restart] starting application"
nohup java ${JAVA_OPTS:-} -jar "$APP_JAR" > "$LOG_FILE" 2>&1 &
NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"
echo "[restart] new pid: $NEW_PID"

wait_started() {
  for _ in {1..60}; do
    if ! kill -0 "$NEW_PID" 2>/dev/null; then
      echo "[smoke] application process exited during startup"
      tail -n 160 "$LOG_FILE" || true
      rm -f "$PID_FILE"
      return 1
    fi
    if grep -q "Started PlatformApplication" "$LOG_FILE" 2>/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "[smoke] timeout waiting for Spring Boot startup"
  tail -n 160 "$LOG_FILE" || true
  return 1
}

wait_started

AUTH_ARGS=()
if [[ -n "${PLATFORM_ACCESS_TOKEN:-}" ]]; then
  AUTH_ARGS=(-H "Authorization: Bearer ${PLATFORM_ACCESS_TOKEN}")
fi

wait_http() {
  local url="$1"
  for _ in {1..60}; do
    if curl -fsS --max-time 5 "${AUTH_ARGS[@]}" "$url" >/dev/null 2>&1; then
      return 0
    fi
    if ! kill -0 "$NEW_PID" 2>/dev/null; then
      echo "[smoke] application process exited during startup"
      tail -n 120 "$LOG_FILE" || true
      return 1
    fi
    sleep 1
  done
  echo "[smoke] timeout waiting for $url"
  tail -n 120 "$LOG_FILE" || true
  return 1
}

wait_http "$BASE_URL/"

echo "[smoke] root page OK"
curl -fsS --max-time 10 "${AUTH_ARGS[@]}" "$BASE_URL/api/dashboard/overview" >/dev/null
echo "[smoke] dashboard overview OK"
curl -fsS --max-time 10 "${AUTH_ARGS[@]}" "$BASE_URL/api/dashboard/operations" >/dev/null
echo "[smoke] dashboard operations OK"

if [[ "${SEATUNNEL_REAL_ENABLED:-true}" == "true" ]]; then
  SEATUNNEL_HOME_VALUE="${SEATUNNEL_HOME:-/data/software/seatunnel}"
  if [[ -x "$SEATUNNEL_HOME_VALUE/bin/seatunnel.sh" ]]; then
    echo "[smoke] SeaTunnel executable OK: $SEATUNNEL_HOME_VALUE/bin/seatunnel.sh"
  else
    echo "[smoke] WARN: SeaTunnel real mode is enabled but local executable is not available; use a configured remote SeaTunnel cluster or install it locally"
  fi
fi

if [[ "${DOLPHINSCHEDULER_REAL_ENABLED:-true}" == "true" ]]; then
  if [[ -z "${DOLPHINSCHEDULER_PASSWORD:-}" && -z "${DOLPHINSCHEDULER_TOKEN:-}" ]]; then
    echo "[smoke] DolphinScheduler env credentials are empty; persisted cluster credentials may be used"
  else
    echo "[smoke] DolphinScheduler env credentials are configured"
  fi
fi

echo "[done] deployment and smoke tests passed"
