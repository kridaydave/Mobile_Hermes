#!/usr/bin/env python3
from __future__ import annotations

import json
import shlex
import sys
from pathlib import Path
from typing import Any


APP_DIR = Path.home() / ".mobile-hermes"
CONFIG_PATH = APP_DIR / "config.json"
ROTATION_STATE_PATH = APP_DIR / "rotation_state.json"


def load_json(path: Path, default: dict[str, Any]) -> dict[str, Any]:
    if not path.exists():
        return default
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return default
    return data if isinstance(data, dict) else default


def save_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def choose_key(config: dict[str, Any], provider_id: str) -> dict[str, Any]:
    providers = config.get("providers", {})
    if not isinstance(providers, dict) or provider_id not in providers:
        raise SystemExit(f"Provider is not configured: {provider_id}")
    provider = providers[provider_id]
    if not isinstance(provider, dict):
        raise SystemExit(f"Provider config is invalid: {provider_id}")

    keys = provider.get("keys", [])
    if not isinstance(keys, list) or not keys:
        raise SystemExit(f"Provider has no keys: {provider_id}")

    state = load_json(ROTATION_STATE_PATH, {})
    current_index = int(state.get(provider_id, 0)) % len(keys)
    state[provider_id] = (current_index + 1) % len(keys)
    save_json(ROTATION_STATE_PATH, state)

    models = provider.get("models", [])
    sorted_models = sorted(
        [model for model in models if isinstance(model, dict) and model.get("model")],
        key=lambda model: int(model.get("priority", 100)),
    )
    primary_model = str(sorted_models[0]["model"]) if sorted_models else ""

    return {
        "provider": provider_id,
        "api_key": str(keys[current_index]),
        "base_url": str(provider.get("base_url", "")),
        "api_key_header": str(provider.get("api_key_header", "Authorization")),
        "model": primary_model,
    }


def shell_export(name: str, value: str) -> str:
    return f"export {name}={shlex.quote(value)}"


def main() -> None:
    config = load_json(CONFIG_PATH, {})
    provider_id = sys.argv[1] if len(sys.argv) > 1 else str(config.get("primary_provider", ""))
    if not provider_id:
        raise SystemExit("No primary provider configured")

    selected = choose_key(config, provider_id)
    api_key = selected["api_key"]
    base_url = selected["base_url"]
    model = selected["model"]

    print(shell_export("MOBILE_HERMES_PROVIDER", selected["provider"]))
    print(shell_export("MOBILE_HERMES_MODEL", model))
    print(shell_export("HERMES_MODEL", model))
    print(shell_export("OPENAI_API_KEY", api_key))
    print(shell_export("OPENAI_BASE_URL", base_url))
    print(shell_export("OPENAI_API_BASE", base_url))

    if selected["provider"] == "openrouter":
        print(shell_export("OPENROUTER_API_KEY", api_key))
        print(shell_export("OPENROUTER_BASE_URL", base_url))
    elif selected["provider"] == "opencode":
        print(shell_export("OPENCODE_GO_API_KEY", api_key))
        print(shell_export("OPENCODE_ZEN_API_KEY", api_key))
        print(shell_export("OPENCODE_BASE_URL", base_url))
    elif selected["provider"] == "kilocode":
        print(shell_export("KILOCODE_API_KEY", api_key))
        print(shell_export("KILOCODE_BASE_URL", base_url))


if __name__ == "__main__":
    main()
