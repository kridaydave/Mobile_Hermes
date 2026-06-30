#!/data/data/com.termux/files/usr/bin/sh
set -eu

PROMPT="${1:-}"
PROVIDER="${2:-}"

if [ -z "$PROMPT" ]; then
  echo "Usage: sh mobile-hermes-chat.sh 'message' [provider]" >&2
  exit 2
fi

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ENV_FILE="$HOME/.mobile-hermes/hermes.env"

if [ -n "$PROVIDER" ]; then
  python "$SCRIPT_DIR/mobile-hermes-env.py" "$PROVIDER" > "$ENV_FILE"
else
  python "$SCRIPT_DIR/mobile-hermes-env.py" > "$ENV_FILE"
fi

chmod 600 "$ENV_FILE"
. "$ENV_FILE"

if command -v hermes >/dev/null 2>&1; then
  if hermes chat -q "$PROMPT" 2>/dev/null; then
    exit 0
  fi
  if hermes "$PROMPT" 2>/dev/null; then
    exit 0
  fi
fi

cat <<EOF
Hermes CLI is not ready yet, but Mobile Hermes loaded provider "$MOBILE_HERMES_PROVIDER" and model "$MOBILE_HERMES_MODEL".
Run the backend setup first, then retry this chat.
EOF

