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
text = config_path.read_text(encoding="utf-8").strip()
if not text:
    raise SystemExit(f"Config file is empty: {config_path}")

try:
    config = json.loads(text)
except json.JSONDecodeError as exc:
    raise SystemExit(f"Config file is not valid JSON: {config_path}\n{exc}") from exc

if not isinstance(config, dict):
    raise SystemExit(f"Config file must contain a JSON object: {config_path}")

providers = config.get("providers", {})
if not isinstance(providers, dict):
    providers = {}

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
