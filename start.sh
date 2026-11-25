#!/usr/bin/env bash
set -e

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Directory holding the application log, the console output and the PID file. Override it when the default location is
# not writable by the service user, for example: LOG_PATH=/var/log/cr-qa-management ./start.sh
LOG_PATH="${LOG_PATH:-$APP_HOME/../logs}"
PID_FILE="$LOG_PATH/cr-qa-management.pid"
JAR="$APP_HOME/target/cr-qa-management-0.0.1-SNAPSHOT.jar"

if ! mkdir -p "$LOG_PATH" 2>/dev/null; then
  echo "Cannot create the log directory $LOG_PATH." >&2
  echo "Run again with LOG_PATH pointing at a directory $(id -un) can write, for example:" >&2
  echo "  LOG_PATH=/var/log/cr-qa-management $0" >&2
  exit 1
fi

if [ ! -w "$LOG_PATH" ]; then
  echo "The log directory $LOG_PATH is not writable by $(id -un)." >&2
  echo "Fix its ownership/permissions, or set LOG_PATH to a writable directory." >&2
  exit 1
fi

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Application is already running with PID $(cat "$PID_FILE")"
  exit 0
fi

if [ ! -f "$JAR" ]; then
  echo "Building application..."
  (cd "$APP_HOME" && mvn -q -DskipTests package)
fi

cd "$APP_HOME"

nohup java -DLOG_PATH="$LOG_PATH" -jar "$JAR" > "$LOG_PATH/console.log" 2>&1 &
echo $! > "$PID_FILE"

echo "Application started with PID $(cat "$PID_FILE")"
echo "Console output: $LOG_PATH/console.log"
echo "Application logs: $LOG_PATH/cr-qa-management.log"
