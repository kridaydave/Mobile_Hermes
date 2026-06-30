#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_CONFIG_NAME = "playbox_teacher_config.secret.json"
FALLBACK_KEYS_NAME = "api_keys.txt"


@dataclass(frozen=True)
class ProviderConfig:
    name: str
    base_url: str
    api_key_header: str
    keys: list[str]
    models: list[dict[str, Any]]
    free_only: bool = False


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as source:
        data = json.load(source)
    if not isinstance(data, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return data


def clean_keys(keys: Any) -> list[str]:
    if not isinstance(keys, list):
        return []
    seen: set[str] = set()
    cleaned: list[str] = []
    for item in keys:
        value = str(item).strip()
        if not value or value in seen:
            continue
        seen.add(value)
        cleaned.append(value)
    return cleaned


def clean_models(models: Any) -> list[dict[str, Any]]:
    if not isinstance(models, list):
        return []
    cleaned: list[dict[str, Any]] = []
    for item in models:
        if not isinstance(item, dict):
            continue
        model_name = str(item.get("model", "")).strip()
        if not model_name:
            continue
        cleaned.append(
            {
                "model": model_name,
                "priority": int(item.get("priority", 100)),
                "role": str(item.get("role", "general")),
                "temperature": float(item.get("temperature", 0.4)),
                "max_output_tokens": int(item.get("max_output_tokens", 2048)),
            }
        )
    return sorted(cleaned, key=lambda model: model["priority"])


def provider_from_secret(name: str, raw: dict[str, Any]) -> ProviderConfig:
    keys = clean_keys(raw.get("keys", []))
    models = clean_models(raw.get("models", []))
    return ProviderConfig(
        name=str(raw.get("name", name)),
        base_url=str(raw.get("base_url", "")),
        api_key_header=str(raw.get("api_key_header", "Authorization")),
        keys=keys,
        models=models,
        free_only=bool(raw.get("free_only", False)),
    )


def fallback_openrouter_keys(path: Path) -> list[str]:
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8", errors="ignore")
    return sorted(set(re.findall(r"sk-or-v1-[A-Za-z0-9_-]+", text)))


def build_mobile_config(scratch: Path, telegram_token: str, allowed_user_ids: list[int]) -> dict[str, Any]:
    secret_path = scratch / DEFAULT_CONFIG_NAME
    if not secret_path.exists():
        raise FileNotFoundError(f"Missing Scratch secret config: {secret_path}")

    secret = load_json(secret_path)
    raw_providers = secret.get("providers", {})
    if not isinstance(raw_providers, dict):
        raise ValueError("Scratch secret config must contain a providers object")

    providers: dict[str, Any] = {}
    for provider_id, raw_provider in raw_providers.items():
        if not isinstance(raw_provider, dict):
            continue
        provider = provider_from_secret(str(provider_id), raw_provider)
        if not provider.keys and provider_id == "openrouter":
            provider = ProviderConfig(
                name=provider.name,
                base_url=provider.base_url,
                api_key_header=provider.api_key_header,
                keys=fallback_openrouter_keys(scratch / FALLBACK_KEYS_NAME),
                models=provider.models,
                free_only=provider.free_only,
            )
        if not provider.keys:
            continue
        providers[str(provider_id)] = {
            "name": provider.name,
            "base_url": provider.base_url,
            "api_key_header": provider.api_key_header,
            "keys": provider.keys,
            "models": provider.models,
            "free_only": provider.free_only,
            "rotation": {
                "strategy": "round_robin",
                "on_error": "advance_to_next_key",
            },
        }

    if not providers:
        raise ValueError("No usable providers with keys were found in Scratch")

    primary_provider = "opencode" if "opencode" in providers else sorted(providers.keys())[0]

    return {
        "bridge_host": "127.0.0.1",
        "bridge_port": 8765,
        "primary_provider": primary_provider,
        "providers": providers,
        "telegram": {
            "bot_token": telegram_token,
            "allowed_user_ids": allowed_user_ids,
            "proactive_enabled": True,
        },
        "risk_policy": {
            "ask_before_send_messages": True,
            "ask_before_delete": True,
            "ask_before_purchases": True,
            "ask_before_public_posts": True,
            "ask_before_settings_changes": True,
            "ask_before_sharing_personal_data": True,
        },
    }


def parse_user_ids(values: list[str]) -> list[int]:
    parsed: list[int] = []
    for value in values:
        for chunk in value.split(","):
            chunk = chunk.strip()
            if chunk:
                parsed.append(int(chunk))
    return parsed


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local Mobile Hermes config from Desktop/Scratch secrets.")
    parser.add_argument(
        "--scratch",
        type=Path,
        default=Path.home() / "Desktop" / "Scratch",
        help="Folder containing Scratch secret files.",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("termux") / "config.local.json",
        help="Output config path. This file is gitignored.",
    )
    parser.add_argument("--telegram-token", default="", help="Telegram bot token to include locally.")
    parser.add_argument(
        "--telegram-user-id",
        action="append",
        default=[],
        help="Allowed Telegram user ID. Can be repeated or comma-separated.",
    )
    args = parser.parse_args()

    config = build_mobile_config(
        scratch=args.scratch,
        telegram_token=args.telegram_token.strip(),
        allowed_user_ids=parse_user_ids(args.telegram_user_id),
    )
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(config, indent=2), encoding="utf-8")

    provider_summary = {
        provider_id: {
            "keys": len(provider["keys"]),
            "models": [model["model"] for model in provider["models"]],
        }
        for provider_id, provider in config["providers"].items()
    }
    print(f"Wrote local config to {args.out}")
    print(json.dumps({"primary_provider": config["primary_provider"], "providers": provider_summary}, indent=2))
    print("No API keys were printed. Do not commit config.local.json.")


if __name__ == "__main__":
    main()

