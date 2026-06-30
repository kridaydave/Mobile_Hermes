# Product Spec

## Problem

The user wants a useful 24/7 personal assistant that actually knows them, but they cannot keep a laptop running all the time and do not have a card-backed VPS option.

## Goal

Create an Android-local Mobile Hermes app that runs and controls Hermes Agent through Termux, with proactive support and phone/browser/app automation.

## Success metric

The owner uses Mobile Hermes every day for real tasks. For v1, track:

- Hermes bridge starts successfully.
- ADB connects successfully.
- At least one useful Chrome/browser automation succeeds.
- At least one WhatsApp read/draft workflow succeeds.
- At least one proactive Telegram/app notification is sent.

## Users

- Primary and only v1 user: the owner.
- Device: non-rooted Samsung M53, 6GB RAM, current Android.

## Must have

- Native Android app, sideloaded APK.
- Sleek, fast Hermes-styled UI.
- Termux setup and control.
- Local bridge API on the phone.
- Foreground notification for runtime persistence.
- Wireless ADB automation.
- Chrome/browser automation.
- WhatsApp read and draft workflows.
- YouTube open/search/control workflows.
- Telegram bot channel for chat and proactive alerts.
- Local API-key storage on the phone.
- Primary OpenCode Go-compatible key plus OpenRouter fallback rotation.
- Risk approval prompts for borderline or irreversible actions.

## Should have

- Accessibility fallback for better app control.
- Screenshot/OCR screen reading.
- Boot recovery.
- Logs and diagnostics.
- Exportable memory/config for laptop backup later.

## Won't have in v1

- Voice mode.
- Docker.
- Root requirements.
- Play Store compliance.
- Fully automatic first-time Wireless ADB pairing.

## Risk policy

Hermes may read, search, open apps, navigate pages, and draft messages autonomously. It must ask before:

- sending WhatsApp/Telegram messages
- deleting data
- purchases or payments
- public posts or comments
- changing Android/settings/app settings
- sharing personal files, photos, contacts, or credentials

## Kill condition

Stop or redesign if Android prevents a stable local Hermes runtime plus ADB automation on the target phone.

