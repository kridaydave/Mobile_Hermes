#!/data/data/com.termux/files/usr/bin/sh
set -eu

CONFIG="$HOME/.mobile-hermes/config.json"

if [ ! -f "$CONFIG" ]; then
  echo "No Mobile Hermes config found at $CONFIG"
  echo "Run: sh setup.sh first."
  exit 1
fi

TOKEN="${1:-}"
USER_IDS="${2:-}"

if [ -z "$TOKEN" ]; then
  printf "Telegram bot token: "
  stty -echo
  read -r TOKEN
  stty echo
  printf "\n"
fi

if [ -z "$USER_IDS" ]; then
  printf "Allowed Telegram user IDs, comma-separated (leave blank if unknown): "
  read -r USER_IDS
fi

python - "$CONFIG" "$TOKEN" "$USER_IDS" <<'PY'
import json
import sys
from pathlib import Path

config_path = Path(sys.argv[1])
token = sys.argv[2].strip()
raw_user_ids = sys.argv[3].strip()

if not token:
    raise SystemExit("Telegram token cannot be empty")

config = json.loads(config_path.read_text(encoding="utf-8"))
telegram = config.setdefault("telegram", {})
telegram["bot_token"] = token
telegram["proactive_enabled"] = True

if raw_user_ids:
    user_ids = []
    for chunk in raw_user_ids.split(","):
        chunk = chunk.strip()
        if chunk:
            user_ids.append(int(chunk))
    telegram["allowed_user_ids"] = user_ids

config_path.write_text(json.dumps(config, indent=2), encoding="utf-8")
print("Telegram config updated. Token was not printed.")
PY

chmod 600 "$CONFIG"

