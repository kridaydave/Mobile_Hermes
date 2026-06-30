#!/data/data/com.termux/files/usr/bin/sh
set -eu

APP_DIR="$HOME/.mobile-hermes"
MANIFEST="$APP_DIR/install-manifest.json"
YES="${1:-}"

if [ ! -f "$MANIFEST" ]; then
  echo "No Mobile Hermes install manifest found at $MANIFEST."
  echo "Nothing was removed."
  exit 0
fi

echo "Mobile Hermes cleanup will remove only paths and proot distros recorded in:"
echo "  $MANIFEST"
echo
echo "It will NOT remove Termux, unrelated packages, unrelated proot distros, broad home directories,"
echo "Android app data, or source API-key files outside Mobile Hermes-owned copies."
echo

python - "$MANIFEST" <<'PY'
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
print("Recorded owned paths:")
for path in manifest.get("owned_paths", []):
    print(f"  - {path}")
print("Recorded owned proot distros:")
for distro in manifest.get("owned_proot_distros", []):
    print(f"  - {distro}")
PY

if [ "$YES" != "--yes" ]; then
  printf "\nType DELETE MOBILE HERMES to continue: "
  IFS= read -r CONFIRM
  if [ "$CONFIRM" != "DELETE MOBILE HERMES" ]; then
    echo "Cleanup cancelled."
    exit 0
  fi
fi

if [ -f "$APP_DIR/bridge.pid" ]; then
  PID="$(cat "$APP_DIR/bridge.pid" 2>/dev/null || true)"
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" || true
  fi
fi

if command -v proot-distro >/dev/null 2>&1; then
  python - "$MANIFEST" <<'PY' | while IFS= read -r DISTRO; do
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
for distro in manifest.get("owned_proot_distros", []):
    if distro == "ubuntu":
        print(distro)
PY
    proot-distro remove "$DISTRO" || true
  done
fi

python - "$MANIFEST" <<'PY'
import json
import shutil
import sys
from pathlib import Path

manifest_path = Path(sys.argv[1]).expanduser().resolve()
home = Path.home().resolve()
repo = (home / "Mobile_Hermes").resolve()
app_dir = (home / ".mobile-hermes").resolve()
allowed_roots = {repo, app_dir}

manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
for raw_path in manifest.get("owned_paths", []):
    path = Path(raw_path).expanduser().resolve()
    if not any(path == root or root in path.parents for root in allowed_roots):
        print(f"Skipped unsafe path outside Mobile Hermes ownership: {path}")
        continue
    if path.exists():
        if path.is_dir():
            shutil.rmtree(path)
        else:
            path.unlink()
        print(f"Removed {path}")
PY

echo "Mobile Hermes cleanup complete."
