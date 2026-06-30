#!/usr/bin/env python3
from __future__ import annotations

import json
import shlex
import subprocess
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


APP_DIR = Path.home() / ".mobile-hermes"
CONFIG_PATH = APP_DIR / "config.json"
MANIFEST_PATH = APP_DIR / "install-manifest.json"
SCREENSHOT_PATH = APP_DIR / "last_screen.png"
UI_DUMP_DEVICE_PATH = "/sdcard/mobile_hermes_window.xml"
ROTATION_STATE_PATH = APP_DIR / "rotation_state.json"
SCRIPT_DIR = Path(__file__).resolve().parent


@dataclass(frozen=True)
class BridgeConfig:
    host: str = "127.0.0.1"
    port: int = 8765


def load_config() -> dict[str, Any]:
    if not CONFIG_PATH.exists():
        return {}
    with CONFIG_PATH.open("r", encoding="utf-8") as config_file:
        return json.load(config_file)


def bridge_config() -> BridgeConfig:
    raw = load_config()
    return BridgeConfig(
        host=str(raw.get("bridge_host", "127.0.0.1")),
        port=int(raw.get("bridge_port", 8765)),
    )


def save_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def load_rotation_state() -> dict[str, int]:
    if not ROTATION_STATE_PATH.exists():
        return {}
    try:
        with ROTATION_STATE_PATH.open("r", encoding="utf-8") as state_file:
            state = json.load(state_file)
    except json.JSONDecodeError:
        return {}
    return {str(key): int(value) for key, value in state.items()} if isinstance(state, dict) else {}


def provider_summary() -> dict[str, Any]:
    raw = load_config()
    providers = raw.get("providers", {})
    if not isinstance(providers, dict):
        providers = {}
    summary: dict[str, Any] = {}
    for provider_id, provider in providers.items():
        if not isinstance(provider, dict):
            continue
        keys = provider.get("keys", [])
        models = provider.get("models", [])
        summary[str(provider_id)] = {
            "name": provider.get("name", provider_id),
            "base_url": provider.get("base_url", ""),
            "key_count": len(keys) if isinstance(keys, list) else 0,
            "models": [model.get("model", "") for model in models if isinstance(model, dict)],
            "rotation": provider.get("rotation", {"strategy": "round_robin"}),
        }
    return {
        "ok": True,
        "primary_provider": raw.get("primary_provider", ""),
        "providers": summary,
        "telegram_configured": bool(raw.get("telegram", {}).get("bot_token", "")) if isinstance(raw.get("telegram"), dict) else False,
    }


def setup_status() -> dict[str, Any]:
    termux_properties = Path.home() / ".termux" / "termux.properties"
    external_commands = False
    if termux_properties.exists():
        external_commands = "allow-external-apps=true" in termux_properties.read_text(
            encoding="utf-8",
            errors="replace",
        )

    config_present = CONFIG_PATH.exists()
    provider_count = 0
    if config_present:
        raw = load_config()
        providers = raw.get("providers", {})
        provider_count = len(providers) if isinstance(providers, dict) else 0

    pid = None
    pid_file = APP_DIR / "bridge.pid"
    if pid_file.exists():
        pid = pid_file.read_text(encoding="utf-8", errors="replace").strip()

    hermes_check = run_command(["sh", "-lc", "command -v hermes >/dev/null 2>&1"], timeout=5)
    return {
        "ok": True,
        "termux_home": str(Path.home()),
        "external_commands": external_commands,
        "external_commands_hint": "~/.termux/termux.properties must contain allow-external-apps=true, then Termux must be restarted.",
        "config_present": config_present,
        "provider_count": provider_count,
        "hermes_cli": hermes_check["ok"],
        "bridge_pid": pid,
        "manifest_present": MANIFEST_PATH.exists(),
        "pure_termux_default": True,
        "telegram_optional": True,
    }


def next_provider_key(provider_id: str) -> dict[str, Any]:
    raw = load_config()
    providers = raw.get("providers", {})
    if not isinstance(providers, dict) or provider_id not in providers:
        return {"ok": False, "error": f"provider not configured: {provider_id}"}
    provider = providers[provider_id]
    if not isinstance(provider, dict):
        return {"ok": False, "error": f"invalid provider config: {provider_id}"}
    keys = provider.get("keys", [])
    if not isinstance(keys, list) or not keys:
        return {"ok": False, "error": f"provider has no keys: {provider_id}"}

    state = load_rotation_state()
    current_index = state.get(provider_id, 0) % len(keys)
    state[provider_id] = (current_index + 1) % len(keys)
    save_json(ROTATION_STATE_PATH, state)

    models = provider.get("models", [])
    primary_model = ""
    if isinstance(models, list) and models:
        sorted_models = sorted(
            [model for model in models if isinstance(model, dict)],
            key=lambda model: int(model.get("priority", 100)),
        )
        if sorted_models:
            primary_model = str(sorted_models[0].get("model", ""))

    return {
        "ok": True,
        "provider": provider_id,
        "base_url": provider.get("base_url", ""),
        "api_key_header": provider.get("api_key_header", "Authorization"),
        "key_index": current_index,
        "key_count": len(keys),
        "api_key": str(keys[current_index]),
        "primary_model": primary_model,
    }


def run_command(args: list[str], timeout: int = 20) -> dict[str, Any]:
    try:
        result = subprocess.run(
            args,
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
    except FileNotFoundError as exc:
        return {
            "ok": False,
            "returncode": 127,
            "stdout": "",
            "stderr": f"missing executable: {exc.filename}",
            "command": args,
        }
    except subprocess.TimeoutExpired as exc:
        return {
            "ok": False,
            "returncode": 124,
            "stdout": exc.stdout or "",
            "stderr": f"timed out after {timeout}s",
            "command": args,
        }

    return {
        "ok": result.returncode == 0,
        "returncode": result.returncode,
        "stdout": result.stdout,
        "stderr": result.stderr,
        "command": args,
    }


def run_binary_command(args: list[str], timeout: int = 20) -> tuple[dict[str, Any], bytes]:
    try:
        result = subprocess.run(
            args,
            check=False,
            capture_output=True,
            timeout=timeout,
        )
    except FileNotFoundError as exc:
        return (
            {
                "ok": False,
                "returncode": 127,
                "stdout": "",
                "stderr": f"missing executable: {exc.filename}",
                "command": args,
            },
            b"",
        )
    except subprocess.TimeoutExpired as exc:
        return (
            {
                "ok": False,
                "returncode": 124,
                "stdout": "",
                "stderr": f"timed out after {timeout}s",
                "command": args,
            },
            b"",
        )

    return (
        {
            "ok": result.returncode == 0,
            "returncode": result.returncode,
            "stdout": "",
            "stderr": result.stderr.decode("utf-8", errors="replace"),
            "command": args,
        },
        result.stdout,
    )


def adb(*args: str, timeout: int = 20) -> dict[str, Any]:
    return run_command(["adb", *args], timeout=timeout)


def shell_escape_text_for_adb(text: str) -> str:
    return text.replace("%", "%25").replace(" ", "%s")


def risky_action(text: str) -> str | None:
    lowered = text.lower()
    risky_terms = [
        "send whatsapp",
        "send message",
        "delete",
        "buy",
        "purchase",
        "pay",
        "post",
        "comment",
        "change setting",
        "share contact",
        "share file",
    ]
    for term in risky_terms:
        if term in lowered:
            return term
    return None


def handle_command(text: str) -> dict[str, Any]:
    risk = risky_action(text)
    if risk:
        return {
            "ok": False,
            "needs_approval": True,
            "reason": f"Command matched risky action: {risk}",
            "text": text,
        }

    lowered = text.lower()
    if "open chrome" in lowered or "browser" in lowered:
        return launch_package("com.android.chrome")
    if "open youtube" in lowered:
        return launch_package("com.google.android.youtube")
    if "open whatsapp" in lowered:
        return launch_package("com.whatsapp")
    if "screenshot" in lowered or "screen" in lowered:
        return screenshot()

    return {
        "ok": True,
        "message": "Bridge received command. Hermes Agent routing is the next integration step.",
        "text": text,
    }


def chat(text: str) -> dict[str, Any]:
    if not text.strip():
        return {"ok": False, "error": "chat text is empty"}
    result = run_command(["sh", str(SCRIPT_DIR / "mobile-hermes-chat.sh"), text], timeout=120)
    return {
        "ok": result["ok"],
        "action": "chat",
        "response": result["stdout"].strip() if result["stdout"].strip() else result["stderr"].strip(),
        "result": result,
    }


def launch_package(package_name: str) -> dict[str, Any]:
    result = adb("shell", "monkey", "-p", package_name, "-c", "android.intent.category.LAUNCHER", "1")
    return {"ok": result["ok"], "action": "launch_package", "package": package_name, "result": result}


def tap(x: int, y: int) -> dict[str, Any]:
    result = adb("shell", "input", "tap", str(x), str(y))
    return {"ok": result["ok"], "action": "tap", "x": x, "y": y, "result": result}


def type_text(text: str) -> dict[str, Any]:
    result = adb("shell", "input", "text", shell_escape_text_for_adb(text))
    return {"ok": result["ok"], "action": "type_text", "result": result}


def swipe(x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300) -> dict[str, Any]:
    result = adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(duration_ms))
    return {"ok": result["ok"], "action": "swipe", "result": result}


def screenshot() -> dict[str, Any]:
    APP_DIR.mkdir(parents=True, exist_ok=True)
    result, png_bytes = run_binary_command(["adb", "exec-out", "screencap", "-p"], timeout=20)
    if not result["ok"]:
        return {"ok": False, "action": "screenshot", "result": result}
    SCREENSHOT_PATH.write_bytes(png_bytes)
    return {"ok": True, "action": "screenshot", "path": str(SCREENSHOT_PATH)}


def ui_dump() -> dict[str, Any]:
    dump_result = adb("shell", "uiautomator", "dump", UI_DUMP_DEVICE_PATH, timeout=20)
    if not dump_result["ok"]:
        return {"ok": False, "action": "ui_dump", "result": dump_result}
    read_result = adb("shell", "cat", UI_DUMP_DEVICE_PATH, timeout=20)
    return {"ok": read_result["ok"], "action": "ui_dump", "xml": read_result["stdout"], "result": read_result}


class Handler(BaseHTTPRequestHandler):
    server_version = "MobileHermesBridge/0.1"

    def do_GET(self) -> None:
        if self.path == "/health":
            self.respond({"ok": True, "service": "mobile-hermes-bridge"})
            return
        if self.path == "/adb/devices":
            self.respond(adb("devices"))
            return
        if self.path == "/screen/dump":
            self.respond(ui_dump())
            return
        if self.path == "/providers":
            self.respond(provider_summary())
            return
        if self.path == "/setup/status":
            self.respond(setup_status())
            return
        self.respond({"ok": False, "error": "not found"}, HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:
        body = self.read_json()
        if self.path == "/command":
            self.respond(handle_command(str(body.get("text", ""))))
            return
        if self.path == "/chat":
            self.respond(chat(str(body.get("text", ""))))
            return
        if self.path == "/adb/connect":
            host = str(body.get("host", ""))
            port = int(body.get("port", 0))
            self.respond(adb("connect", f"{host}:{port}"))
            return
        if self.path == "/adb/pair":
            host = str(body.get("host", ""))
            port = int(body.get("port", 0))
            code = str(body.get("code", ""))
            self.respond(adb("pair", f"{host}:{port}", code))
            return
        if self.path == "/automation/launch":
            self.respond(launch_package(str(body.get("package", ""))))
            return
        if self.path == "/automation/tap":
            self.respond(tap(int(body["x"]), int(body["y"])))
            return
        if self.path == "/automation/type":
            self.respond(type_text(str(body.get("text", ""))))
            return
        if self.path == "/automation/swipe":
            self.respond(
                swipe(
                    int(body["x1"]),
                    int(body["y1"]),
                    int(body["x2"]),
                    int(body["y2"]),
                    int(body.get("duration_ms", 300)),
                )
            )
            return
        if self.path == "/providers/next-key":
            provider_id = str(body.get("provider", load_config().get("primary_provider", "")))
            self.respond(next_provider_key(provider_id))
            return
        self.respond({"ok": False, "error": "not found"}, HTTPStatus.NOT_FOUND)

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length == 0:
            return {}
        raw_body = self.rfile.read(length).decode("utf-8")
        try:
            parsed = json.loads(raw_body)
        except json.JSONDecodeError:
            return {}
        return parsed if isinstance(parsed, dict) else {}

    def respond(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        encoded = json.dumps(payload, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, format: str, *args: Any) -> None:
        message = format % args
        print(f"{self.address_string()} - {message}")


def main() -> None:
    APP_DIR.mkdir(parents=True, exist_ok=True)
    config = bridge_config()
    server = ThreadingHTTPServer((config.host, config.port), Handler)
    print(f"Mobile Hermes bridge listening on http://{config.host}:{config.port}")
    print(f"Config: {shlex.quote(str(CONFIG_PATH))}")
    server.serve_forever()


if __name__ == "__main__":
    main()
