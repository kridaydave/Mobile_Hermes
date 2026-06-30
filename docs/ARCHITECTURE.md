# Architecture

## Decision

Mobile Hermes is phone-local. The native Android app is the control surface; Termux hosts Hermes and the local bridge; Wireless ADB automates the phone.

## Components

```text
Android App
  - Compose UI
  - foreground service
  - setup wizard
  - risk approval prompts
  - bridge client
  - Telegram status controls

Termux Runtime
  - Hermes Agent
  - mobile_hermes_bridge.py
  - adb client
  - OCR/screenshot utilities
  - local config and logs

Automation
  - adb shell am start
  - adb shell input tap/text/swipe/keyevent
  - uiautomator dump
  - screencap
  - optional OCR
  - optional Accessibility fallback
```

## Data flow

1. User opens Mobile Hermes or sends Telegram message.
2. Android app calls `http://127.0.0.1:8765`.
3. Termux bridge routes the command to Hermes or automation tools.
4. Automation tools call ADB against the same phone.
5. Bridge returns status, logs, screenshots, or action result.
6. Risky actions return `needs_approval` before execution.

## Why not VPS

The user has no credit/debit card and wants local phone hosting. A VPS would improve uptime, but it is not available as the default architecture.

## Why not desktop browser automation

Android Chrome is not desktop Chrome. v1 automation uses ADB and screen/UI inspection instead of Playwright-style desktop browser control.

## Failure modes

| Failure | Mitigation |
|---|---|
| Android kills background work | Foreground service, battery optimization instructions, restart controls |
| ADB disconnects | Health check, reconnect command, visible setup wizard |
| Wireless ADB pairing resets | Manual re-pair flow |
| WhatsApp UI changes | Confirm-before-send and UI/OCR fallback |
| Battery drain | Standby/active/sleep modes |
| API key leakage | Phone-local config, no keys in git, no memory logging |
| Bridge offline | App status card and Termux launcher |

## Revisit if

- The phone cannot keep Termux alive reliably enough.
- ADB pairing is too unstable for daily use.
- WhatsApp automation needs safer official APIs.
- The user later gets reliable VPS access.

