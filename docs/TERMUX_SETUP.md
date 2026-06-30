# Termux Setup

## Prerequisites

- Termux installed from F-Droid.
- Android Developer Options enabled.
- Wireless Debugging enabled when pairing ADB.

## Install

Copy this repo to the phone or download the `termux/` folder, then run:

```sh
cd termux
sh setup.sh
```

The setup script installs Python, Git, Android tools, and creates local config/log directories.

## Import keys from Desktop/Scratch

Do not paste API keys into git. On the Windows desktop, create a local ignored config from `Desktop/Scratch`:

```powershell
python tools\import_scratch_keys.py
```

This reads `C:\Users\NewAdmin\Desktop\Scratch\playbox_teacher_config.secret.json` and writes:

```text
termux/config.local.json
```

That file is ignored by git. Transfer it to the phone in one of these locations:

```text
Mobile_Hermes/termux/config.local.json
/sdcard/Download/mobile-hermes-config.json
```

Then run:

```sh
cd Mobile_Hermes/termux
sh setup.sh
```

`setup.sh` installs the local config into:

```text
~/.mobile-hermes/config.json
```

The config supports provider-level round-robin key rotation for OpenRouter, OpenCode, and KiloCode, with model lists preserved from Scratch.

## Telegram token

If you want the importer to include a Telegram bot token without editing JSON manually, run:

```powershell
python tools\import_scratch_keys.py --telegram-token "YOUR_BOT_TOKEN" --telegram-user-id "YOUR_TELEGRAM_USER_ID"
```

If you do not know your numeric Telegram user ID yet, leave it empty for now and add it later to `~/.mobile-hermes/config.json` on the phone.

On the phone, you can set or update the token locally:

```sh
sh mobile-hermes-set-telegram.sh
```

Or pass values directly:

```sh
sh mobile-hermes-set-telegram.sh "BOT_TOKEN" "123456789"
```

To verify local provider/model setup without printing keys:

```sh
sh mobile-hermes-config-summary.sh
```

## Start the bridge

```sh
sh mobile-hermes-start.sh
```

The bridge listens on:

```text
http://127.0.0.1:8765
```

## Wireless ADB

Pairing requires a manual Android security step:

1. Android Settings > Developer options > Wireless debugging.
2. Pair device with pairing code.
3. Use the pair host/port and code in the bridge:

```sh
adb pair 127.0.0.1:PAIR_PORT PAIR_CODE
adb connect 127.0.0.1:CONNECT_PORT
```

Some Android builds do not expose stable localhost pairing ports. If localhost fails, use the IP and port shown by Wireless Debugging.

## Secrets

Put model keys in:

```text
~/.mobile-hermes/config.json
```

Never commit API keys.

The bridge exposes a redacted provider summary at:

```text
GET http://127.0.0.1:8765/providers
```

It also exposes an internal key-rotation helper for future Hermes integration:

```text
POST http://127.0.0.1:8765/providers/next-key
{"provider":"openrouter"}
```

That endpoint returns the next key for the local Hermes process. Do not expose the bridge outside localhost.
