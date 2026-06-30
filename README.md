# Mobile Hermes

Mobile Hermes is a personal Android control app for running Hermes Agent locally on a phone through Termux. The first target device is a non-rooted Samsung M53 with 6GB RAM.

The app is designed for one owner, sideloaded APK distribution, and local-first operation. It uses a native Android UI to monitor and control a Termux-hosted Hermes runtime, with Wireless ADB as the main automation channel for Chrome, WhatsApp, YouTube, and other apps.

## Goals

- Run Hermes Agent from Termux on Android.
- Provide a sleek native Android control surface.
- Keep Hermes reachable through the app and Telegram.
- Support proactive notifications and scheduled tasks.
- Automate Chrome, WhatsApp, YouTube, and browser workflows using ADB.
- Ask before risky actions such as sending messages, deleting data, purchases, posting, or changing settings.
- Provide local in-app chat so Telegram is optional.
- Guide backend setup from the app for pure Termux or Ubuntu/proot.

## Non-goals for v1

- Voice assistant mode.
- Docker isolation.
- Root-only automation.
- Play Store distribution.
- Fully automatic Wireless ADB pairing. Android requires at least one manual pairing step.

## Repository layout

```text
app/                    Native Android app
termux/                 Termux setup scripts and local bridge server
docs/                   Product, architecture, and safety notes
memory/                 Local crew notes, not part of the mobile runtime
```

## First install flow

1. Install Termux from F-Droid.
2. Put `config.local.json` in `Mobile_Hermes/termux/` or `/sdcard/Download/mobile-hermes-config.json`.
3. Open Mobile Hermes and use the backend wizard, or run `termux/mobile-hermes-bootstrap.sh pure-termux` inside Termux.
4. Start the foreground service in the app.
5. Chat through the app. Telegram is optional.
6. Enable Wireless Debugging later when you want browser/app automation.

## Current status

This is the initial scaffold. The Android source, Termux bridge, and implementation docs are laid out, but a local Android build has not been verified on this Windows machine because Java/Gradle are not installed on PATH.

## Cloud APK builds

GitHub Actions builds a debug APK on pushes, pull requests, and manual runs. The workflow stages the APK under `downloads/` on the runner and uploads that folder as an artifact named `downloads`.

To download the APK:

1. Open the repository on GitHub.
2. Go to Actions.
3. Open the latest `Build APK` run.
4. Download the `downloads` artifact.
