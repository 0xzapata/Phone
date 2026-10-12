# Agent bridge plan (fork of Fossify Phone)

Goal: keep this app as the default dialer, and add a Claude agent hook for call automations.
Distribution: sideload only. Not for Google Play.

## Verified facts (2026-10-12)

- Call added (incoming or outgoing): `CallService.onCallAdded` in `app/src/main/kotlin/org/fossify/phone/services/CallService.kt`.
- Call ended: `CallService.onCallRemoved` in the same file. Duration comes from `Call.Details.connectTimeMillis`, already used in `extensions/Call.kt`.
- The in-call service process lives through the call and can end after. The Claude call must run in an expedited WorkManager job, not in the service.
- The dialer role exempts the app from the restricted standby bucket. That helps the job run.
- The API endpoint, the router traps, and the Jev gate are verified in the Messages fork plan. Same findings apply here.

## Data shape

One `AgentEvent`: kind (`call_started`, `call_ended`), number, direction, duration seconds, missed flag, timestamp.
Same `AgentPolicy` and `AgentConfig` as the Messages fork.

## Changes

1. Manifest: add `android.permission.INTERNET`. Add a backup rule that excludes the agent preferences file.
2. Dependency: `androidx.work:work-runtime-ktx` 2.12.0.
3. `CallService.onCallAdded` and `onCallRemoved`: if the number is on the allowlist, enqueue one expedited `AgentWorker` with the event. Unique work name per call handle and kind, policy KEEP.
4. `app/src/main/kotlin/org/fossify/phone/agent/AgentWorker.kt`: same 3 steps as Messages. Jev gate runs on the contact name and number only, because a call has no body. Tools: `send_sms` (intent to the Messages fork), `lookup_contact`. Policy caps in code.
5. Skills: folder picker, persisted URI, `*.md` files, same as Messages.
6. Settings screen: section "AI agent", same fields as Messages.

## Build and sideload

- Build on the devbox: `./gradlew assembleFossDebug`.
- Install from a Tailscale Serve URL on the phone, or `adb install` over wireless debugging.

## Out of scope

- SMS receive events live in the Fossify Messages fork.
- No call audio or transcription. Android blocks it for third-party apps.
