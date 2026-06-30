#!/data/data/com.termux/files/usr/bin/sh
set -eu

pkg update -y
pkg install -y android-tools git python termux-api

CONFIG="$HOME/.mobile-hermes/config.json"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
LOCAL_CONFIG="$SCRIPT_DIR/config.local.json"
DOWNLOAD_CONFIG="/sdcard/Download/mobile-hermes-config.json"

mkdir -p "$HOME/.mobile-hermes/logs"
python "$SCRIPT_DIR/mobile-hermes-manifest.py" init
python "$SCRIPT_DIR/mobile-hermes-manifest.py" add-path "$HOME/.mobile-hermes/config.json" "$HOME/.mobile-hermes/logs" "$HOME/.mobile-hermes/hermes.env" "$HOME/.mobile-hermes/rotation_state.json" "$HOME/.mobile-hermes/bridge.pid" "$HOME/.mobile-hermes/last_screen.png"

validate_config() {
  python - "$1" <<'PY'
import json
import sys
from pathlib import Path

config_path = Path(sys.argv[1])
if not config_path.exists():
    raise SystemExit(f"missing config: {config_path}")
text = config_path.read_text(encoding="utf-8").strip()
if not text:
    raise SystemExit(f"empty config: {config_path}")
config = json.loads(text)
if not isinstance(config, dict):
    raise SystemExit(f"config must be a JSON object: {config_path}")
PY
}

install_config() {
  source_config="$1"
  validate_config "$source_config"
  cp "$source_config" "$CONFIG"
  chmod 600 "$CONFIG"
  echo "Installed local Mobile Hermes config from $source_config."
}

if [ -f "$LOCAL_CONFIG" ]; then
  install_config "$LOCAL_CONFIG"
elif [ -f "$DOWNLOAD_CONFIG" ]; then
  install_config "$DOWNLOAD_CONFIG"
elif [ ! -f "$CONFIG" ]; then
  cat > "$CONFIG" <<'JSON'
{
  "bridge_host": "127.0.0.1",
  "bridge_port": 8765,
  "primary_provider": "opencode",
  "providers": {},
  "telegram": {
    "bot_token": "",
    "allowed_user_ids": [],
    "proactive_enabled": true
  },
  "risk_policy": {
    "ask_before_send_messages": true,
    "ask_before_delete": true,
    "ask_before_purchases": true,
    "ask_before_public_posts": true,
    "ask_before_settings_changes": true,
    "ask_before_sharing_personal_data": true
  }
}
JSON
fi

chmod +x mobile-hermes-start.sh mobile-hermes-stop.sh mobile-hermes-status.sh mobile-hermes-config-summary.sh mobile-hermes-set-telegram.sh mobile-hermes-bootstrap.sh mobile-hermes-chat.sh mobile-hermes-env.py mobile-hermes-manifest.py mobile-hermes-cleanup.sh 2>/dev/null || true

echo "Mobile Hermes Termux setup complete."
echo "Config lives at $CONFIG"
echo "Check config without printing keys: sh mobile-hermes-config-summary.sh"
echo "Set Telegram token locally: sh mobile-hermes-set-telegram.sh"
echo "Install backend: sh mobile-hermes-bootstrap.sh pure-termux"
echo "Run: sh mobile-hermes-start.sh"
