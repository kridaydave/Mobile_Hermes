#!/data/data/com.termux/files/usr/bin/sh
set -eu

PID_FILE="$HOME/.mobile-hermes/bridge.pid"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Mobile Hermes bridge running with PID $(cat "$PID_FILE")."
else
  echo "Mobile Hermes bridge is not running."
fi

echo "ADB devices:"
adb devices || true

echo
if [ -x "./mobile-hermes-config-summary.sh" ]; then
  sh ./mobile-hermes-config-summary.sh || true
fi

echo
if command -v hermes >/dev/null 2>&1; then
  echo "Hermes CLI: $(command -v hermes)"
else
  echo "Hermes CLI: not installed yet"
fi
