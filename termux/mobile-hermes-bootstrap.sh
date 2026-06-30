#!/data/data/com.termux/files/usr/bin/sh
set -eu

MODE="${1:-pure-termux}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

install_common() {
  pkg update -y
  pkg install -y android-tools curl git nodejs python ripgrep termux-api
  sh "$SCRIPT_DIR/setup.sh"
}

install_hermes() {
  if command -v hermes >/dev/null 2>&1; then
    echo "Hermes CLI already installed: $(command -v hermes)"
    return
  fi

  echo "Installing Hermes Agent with the official installer..."
  curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash
  if [ -f "$HOME/.bashrc" ]; then
    # shellcheck disable=SC1090
    . "$HOME/.bashrc" || true
  fi
}

install_pure_termux() {
  install_common
  install_hermes
  python "$SCRIPT_DIR/mobile-hermes-env.py" > "$HOME/.mobile-hermes/hermes.env"
  chmod 600 "$HOME/.mobile-hermes/hermes.env"
  echo "Pure Termux backend ready."
  echo "Start bridge: sh mobile-hermes-start.sh"
}

install_ubuntu() {
  install_common
  pkg install -y proot-distro
  proot-distro install ubuntu || true
  proot-distro login ubuntu -- bash -lc 'set -eu; apt update; apt install -y curl git python3 nodejs npm ripgrep; curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash'
  echo "Ubuntu/proot backend installed. Pure Termux bridge still owns config and phone automation."
}

case "$MODE" in
  pure-termux)
    install_pure_termux
    ;;
  ubuntu)
    install_ubuntu
    ;;
  *)
    echo "Unknown backend mode: $MODE" >&2
    echo "Use: pure-termux or ubuntu" >&2
    exit 2
    ;;
esac
