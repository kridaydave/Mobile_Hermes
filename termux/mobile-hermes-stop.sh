#!/data/data/com.termux/files/usr/bin/sh
set -eu

PID_FILE="$HOME/.mobile-hermes/bridge.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "Mobile Hermes bridge is not running."
  exit 0
fi

PID="$(cat "$PID_FILE")"
if kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  echo "Stopped Mobile Hermes bridge with PID $PID."
else
  echo "Stale PID file removed."
fi

rm -f "$PID_FILE"

