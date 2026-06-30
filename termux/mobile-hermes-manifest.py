#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
import time
from pathlib import Path
from typing import Any


APP_DIR = Path.home() / ".mobile-hermes"
MANIFEST_PATH = APP_DIR / "install-manifest.json"


def load_manifest() -> dict[str, Any]:
    if not MANIFEST_PATH.exists():
        return {
            "schema": 1,
            "created_at": int(time.time()),
            "owned_paths": [],
            "owned_proot_distros": [],
            "installed_packages": [],
            "notes": [],
        }
    try:
        data = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        data = {}
    if not isinstance(data, dict):
        data = {}
    data.setdefault("schema", 1)
    data.setdefault("created_at", int(time.time()))
    data.setdefault("owned_paths", [])
    data.setdefault("owned_proot_distros", [])
    data.setdefault("installed_packages", [])
    data.setdefault("notes", [])
    return data


def save_manifest(manifest: dict[str, Any]) -> None:
    manifest["updated_at"] = int(time.time())
    APP_DIR.mkdir(parents=True, exist_ok=True)
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2, sort_keys=True), encoding="utf-8")
    MANIFEST_PATH.chmod(0o600)


def add_unique(manifest: dict[str, Any], key: str, value: str) -> None:
    values = manifest.setdefault(key, [])
    if not isinstance(values, list):
        values = []
        manifest[key] = values
    if value not in values:
        values.append(value)


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("Usage: mobile-hermes-manifest.py init|add-path|add-proot|add-package VALUE...")

    manifest = load_manifest()
    command = sys.argv[1]

    if command == "init":
        add_unique(manifest, "owned_paths", str(APP_DIR))
    elif command == "add-path":
        for raw_path in sys.argv[2:]:
            add_unique(manifest, "owned_paths", str(Path(raw_path).expanduser()))
    elif command == "add-proot":
        for distro in sys.argv[2:]:
            add_unique(manifest, "owned_proot_distros", distro)
    elif command == "add-package":
        for package in sys.argv[2:]:
            add_unique(manifest, "installed_packages", package)
    elif command == "note":
        note = " ".join(sys.argv[2:]).strip()
        if note:
            add_unique(manifest, "notes", note)
    else:
        raise SystemExit(f"Unknown manifest command: {command}")

    save_manifest(manifest)


if __name__ == "__main__":
    main()
