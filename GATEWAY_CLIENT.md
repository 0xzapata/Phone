# Gateway client (Fossify Phone fork)

The phone is a thin client. It posts each call event to the private gateway and does nothing else. The contract is GATEWAY_CONTRACT.md, section "POST /v1/events (device)".

## What it sends

- `call_started` from `CallService.onCallAdded`, `call_ended` from `CallService.onCallRemoved`.
- Fields: `id` (UUID v4), `kind`, `occurred_at` (RFC 3339 UTC, whole seconds), `number` (normalized; empty when private or unknown), `display_name` (empty when unknown), `direction`, `duration_s` (0 for `call_started`), `missed` (true only for an incoming `call_ended` that never connected).
- Only when the gateway is enabled. An empty allowlist sends every call; else only calls from listed numbers.
- Request: `POST <gateway URL>/v1/events`, `Authorization: Bearer <device token>`, `Content-Type: application/json`. The URL must start with `https://`.

## When and how retries work

- The call hook makes the event, its UUID, and its JSON at once, then stores a WorkManager job before it returns.
- The job runs when a network is connected. Timeouts: connect 15 s, read 30 s.
- 201 or 200: done. A retry resends the same id, so the gateway stores the event one time.
- 401 or 400: stop and post one notification with the status code.
- Any other status, an I/O error, or a timeout: retry with exponential backoff from 30 s. After 10 retries, stop and post one notification.
- The app never logs the body, the number, or the token.
- Settings > Gateway has "Send test event": one `call_started` with display name `test`, through the same job.
