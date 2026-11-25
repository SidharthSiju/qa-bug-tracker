#!/usr/bin/env bash
set -e

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Must match start.sh: the PID file lives in the log directory.
LOG_PATH="${LOG_PATH:-$APP_HOME/../logs}"
PID_FILE="$LOG_PATH/cr-qa-management.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "Application is not running (no PID file found)."
  exit 0
fi

PID="$(cat "$PID_FILE")"

if ! kill -0 "$PID" 2>/dev/null; then
  echo "Application is not running (PID $PID is not active)."
  rm -f "$PID_FILE"
  exit 0
fi

echo "Stopping application with PID $PID..."
kill "$PID"

for _ in $(seq 1 30); do
  if ! kill -0 "$PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

if kill -0 "$PID" 2>/dev/null; then
  echo "Application did not stop gracefully; forcing termination."
  kill -9 "$PID" 2>/dev/null || true
fi

rm -f "$PID_FILE"
echo "Application stopped."
