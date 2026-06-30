# Mobile Hermes Design System

## Visual Grounding

- [Hermes Agent](https://hermes-agent.nousresearch.com/) presents itself as an open-source agent that "grows with you", with install-first utility, terminal fluency, platform connectors, memory, scheduling, delegation, search, and sandboxing as core product ideas. The Mobile Hermes UI should feel like the phone-sized cockpit for those same capabilities: setup, status, chat, and controlled automation.
- [Nous Research](https://nousresearch.com/) uses a dark, research-lab tone with sparse navigation, technical labels, open-source positioning, and an "AI made human" message. Mobile Hermes should borrow the seriousness and machine-room atmosphere without copying web layout.

## Personality

Mobile Hermes is a premium local agent console, not a generic chatbot. It should feel dark, precise, owner-operated, and calm under pressure. Copy should be concise and concrete: "Bridge online", "Providers loaded", "Run setup", "Cleanup install".

## Color

- Background: near-black layered graphite, `#07080C`, `#090A0F`, `#11131B`.
- Primary accent: Hermes gold, `#D9B76A`.
- Secondary amber: `#FFC857` for waiting or partial states.
- Success: `#78E09A`.
- Danger: `#FF6B6B`.
- Text: warm ivory `#EDE8D5`.
- Muted text: `#A9A38E`.
- Hairlines: white at 6-13% opacity, never bright borders.

Avoid purple gradients, blue dashboard chrome, beige luxury cards, and generic Material pastel surfaces.

## Typography Tone

Use system fonts, strong weights, and compact labels. Titles can be bold and architectural; body text should be short, technical, and helpful. Do not use playful chatbot language in setup or cleanup flows.

## Layout

- First screen is the tool, not a marketing landing page.
- Stack modules vertically for mobile: header, setup, runtime, chat, status/cleanup.
- Cards are shallow console panels with 8dp radius, low-contrast borders, and dense spacing.
- Avoid nested cards. Use tiles only for repeated status indicators.
- Keep chat usable with a clear input and recent transcript.

## Components

- Signal orb: compact animated status mark, color-coded by bridge/provider readiness.
- Progress rail: setup checkpoints for Termux installed, external commands enabled, config present, backend installed, bridge running, providers loaded.
- Mode chips: Pure Termux selected by default; Ubuntu/proot clearly optional.
- Console panel: single module container with title, terse subtitle, and actions.
- Danger action: cleanup requires a confirmation dialog and must explain exact ownership boundaries.

## Motion

Motion should communicate runtime state, not decorate.

- Use slow ambient background drift.
- Pulse the signal orb while status is active.
- Fade or alpha-shift status tiles when readiness changes.
- Animate setup reveal/collapse.
- Keep loops slow and cheap for Samsung M53-class hardware.

## Accessibility

- Maintain high contrast for all body text.
- Do not rely on color alone: status text must say online/offline/waiting.
- Avoid cramped rows. Buttons need generous height and readable labels.
- Keep cleanup language explicit because deletion is high-risk.

## Do

- Make local chat feel primary.
- Treat Telegram as optional.
- Show Android/Termux security boundaries honestly.
- Show redacted provider status only.
- Prefer Pure Termux and explain Ubuntu/proot as fallback.

## Do Not

- Do not print, display, or log API keys.
- Do not imply the app can silently enable Termux external commands.
- Do not delete anything outside Mobile Hermes recorded ownership.
- Do not use generic rounded pastel cards or loud decorative gradients.
