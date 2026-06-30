#!/data/data/com.termux/files/usr/bin/sh
set -eu

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
LOG_DIR="$HOME/.mobile-hermes/logs"
PID_FILE="$HOME/.mobile-hermes/bridge.pid"

mkdir -p "$LOG_DIR"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Mobile Hermes bridge already running with PID $(cat "$PID_FILE")."
  exit 0
fi

nohup python "$BASE_DIR/mobile_hermes_bridge.py" > "$LOG_DIR/bridge.log" 2>&1 &
echo "$!" > "$PID_FILE"

echo "Mobile Hermes bridge started with PID $(cat "$PID_FILE")."

