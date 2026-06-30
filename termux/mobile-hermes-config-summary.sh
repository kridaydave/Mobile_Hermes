#!/data/data/com.termux/files/usr/bin/sh
set -eu

CONFIG="$HOME/.mobile-hermes/config.json"

if [ ! -f "$CONFIG" ]; then
  echo "No Mobile Hermes config found at $CONFIG"
  echo "Run: sh setup.sh"
  exit 1
fi

python - "$CONFIG" <<'PY'
import json
import sys
from pathlib import Path

config_path = Path(sys.argv[1])
config = json.loads(config_path.read_text(encoding="utf-8"))
providers = config.get("providers", {})

print(f"Config: {config_path}")
print(f"Primary provider: {config.get('primary_provider', '')}")
print("Providers:")
for provider_id, provider in sorted(providers.items()):
    keys = provider.get("keys", [])
    models = provider.get("models", [])
    print(f"  - {provider_id}: {len(keys)} keys, {len(models)} models")
    for model in models:
        print(f"      [{model.get('priority', 100)}] {model.get('model', '')} ({model.get('role', 'general')})")

telegram = config.get("telegram", {})
print("Telegram:")
print(f"  bot_token_configured: {bool(telegram.get('bot_token'))}")
print(f"  proactive_enabled: {bool(telegram.get('proactive_enabled'))}")
print(f"  allowed_user_ids: {len(telegram.get('allowed_user_ids', []))}")
PY

