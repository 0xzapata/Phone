# Agent bridge plan (fork of Fossify Phone)

Goal: keep this app as the default dialer, and add a Claude agent hook for call automations.
Distribution: sideload only. Not for Google Play.

## Verified insertion points (upstream main, 2026-10-10)

- Call added (incoming or outgoing): `CallService.onCallAdded` in `app/src/main/kotlin/org/fossify/phone/services/CallService.kt`.
- Call ended: `CallService.onCallRemoved` in the same file.
- Call state: `CallManager` in `app/src/main/kotlin/org/fossify/phone/helpers/CallManager.kt`.
- The app has no INTERNET permission and no HTTP client.

## Changes

1. Add `android.permission.INTERNET` to `AndroidManifest.xml`.
2. Add one file `app/src/main/kotlin/org/fossify/phone/agent/AgentBridge.kt`.
   - On call added and call ended for an allowlisted contact, post the event (number, direction, duration, missed or answered) to the Claude API.
   - Model `claude-opus-5-5`, effort `low`, prompt caching on the system prompt.
   - Tools: `send_sms` (intent to the Messages fork), `lookup_contact`.
   - HTTP via `HttpURLConnection`. No new dependency.
   - Runs on a background thread via `ensureBackgroundThread`.
3. Skill workflow: the bridge reads `/sdcard/agent/skills/*.md`. One file is one automation. All files go into the cached system prompt. Edit automations as text, no rebuild.
4. Settings screen: API key, model, per-contact allowlist. The agent acts only on allowlisted contacts.

## Build and sideload

- Build on the devbox: `./gradlew assembleFossDebug`.
- Install from a Tailscale Serve URL on the phone, or `adb install` over wireless debugging.

## Out of scope

- SMS receive events live in the Fossify Messages fork, same bridge shape.
- No call audio or transcription. Android blocks it for third-party apps.
