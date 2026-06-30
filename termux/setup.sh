#!/data/data/com.termux/files/usr/bin/sh
set -eu

pkg update -y
pkg install -y android-tools git python termux-api

mkdir -p "$HOME/.mobile-hermes/logs"

CONFIG="$HOME/.mobile-hermes/config.json"
if [ ! -f "$CONFIG" ]; then
  cat > "$CONFIG" <<'JSON'
{
  "bridge_host": "127.0.0.1",
  "bridge_port": 8765,
  "primary_provider": "opencode_go",
  "opencode_go_api_key": "",
  "openrouter_api_keys": [],
  "telegram_bot_token": "",
  "telegram_allowed_user_ids": [],
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

chmod +x mobile-hermes-start.sh mobile-hermes-stop.sh mobile-hermes-status.sh 2>/dev/null || true

echo "Mobile Hermes Termux setup complete."
echo "Edit $CONFIG with your API keys, then run: sh mobile-hermes-start.sh"

