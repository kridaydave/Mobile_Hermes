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

