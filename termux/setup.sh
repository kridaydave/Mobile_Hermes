#!/data/data/com.termux/files/usr/bin/sh
set -eu

pkg update -y
pkg install -y android-tools git python termux-api

mkdir -p "$HOME/.mobile-hermes/logs"

CONFIG="$HOME/.mobile-hermes/config.json"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
LOCAL_CONFIG="$SCRIPT_DIR/config.local.json"
DOWNLOAD_CONFIG="/sdcard/Download/mobile-hermes-config.json"

if [ -f "$LOCAL_CONFIG" ]; then
  cp "$LOCAL_CONFIG" "$CONFIG"
  chmod 600 "$CONFIG"
  echo "Installed local Mobile Hermes config from $LOCAL_CONFIG."
elif [ -f "$DOWNLOAD_CONFIG" ]; then
  cp "$DOWNLOAD_CONFIG" "$CONFIG"
  chmod 600 "$CONFIG"
  echo "Installed local Mobile Hermes config from $DOWNLOAD_CONFIG."
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

chmod +x mobile-hermes-start.sh mobile-hermes-stop.sh mobile-hermes-status.sh mobile-hermes-config-summary.sh mobile-hermes-set-telegram.sh 2>/dev/null || true

echo "Mobile Hermes Termux setup complete."
echo "Config lives at $CONFIG"
echo "Check config without printing keys: sh mobile-hermes-config-summary.sh"
echo "Set Telegram token locally: sh mobile-hermes-set-telegram.sh"
echo "Run: sh mobile-hermes-start.sh"
