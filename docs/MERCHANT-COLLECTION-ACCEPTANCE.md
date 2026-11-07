# Merchant collection acceptance (2.17.0 candidate)

## Wire contract

The Android collection client uses only the configured HTTPS origin and existing CookieManager session. Every request sets the exact `Origin`, disables redirects/cache, bounds request/response sizes and HTTP timeouts, and never fetches the card issuer URL. It does not use scanner tokens or anonymous check-in.

- `GET /plugins/bitcoin-rewards/{storeId}/pos/api/capability` must return `{ "available": true, "currency": "CAD", "version": 1 }` before amount entry is enabled.
- `POST .../invoices` sends only UUID `operationId` and integer `amountMinor` (CAD cents).
- `GET .../invoices/{operationId}` is the recovery/status authority.
- `POST .../invoices/{operationId}/tap` sends only the strict parser-approved `boltcardUrl`; even a lost/error callback is followed by GET, never a second card dispatch.
- `POST .../invoices/{operationId}/cancel` is followed by GET even when the cancellation request errors or settlement races it.
- Responses echo operation/currency/amount and a stable invoice ID. `review` can have null invoice/QR/expiry after ambiguous creation. Cancelled/expired responses can omit QR. The client never uses a callback ACK as settlement.

These capability and nullable-field details were checked read-only against the paired backend controller/service under `bitcoinrewards-lnurl-safe`; no backend files were modified by the Android task.

## Safety/UX

Only the authoritative waiting-page marker exposes Collect payment; a checked-in customer or reward claim cannot be carried into collection. Staff enters the final Square-calculated total, confirms without a PIN, and hands over the device. The invoice screen has no amount editor. Camera check-in and rewards NFC are paused/guarded before launch. Payment mode consumes NFC even while busy/review/terminal but only a fresh pending, unattempted, current-lifecycle tap can dispatch.

Only operation UUID, origin, store, amount, invoice identity and a tap-dispatched bit are persisted. Persisting the UUID must succeed before create; persisting the tap bit must succeed before tap. Process restart reads the same operation, never creates another invoice or replays a card. Pending, review, unknown/missing operations and authentication/network failures block replacement charges. An unknown operation that never reached the server deliberately requires operator/server reconciliation rather than an unsafe client reset. Terminal results remain locked until explicit staff Finish; return reloads/re-authorizes the trusted check-in display.

The paired server must enforce both BTCPay invoice and Square reward exclusion, reject an already-pending rewards check-in, serialize cancel/tap races, and authoritatively resolve paid/review. Android copy alone is not proof of exclusion.

## Automated / remaining acceptance

JVM coverage exercises integer cents, echo/identity validation, null review/terminal fields, capability gating, response ambiguity, exact-origin/nonredirect session transport, response limits, cancel/settlement races, card ACK/error reconciliation, durable identity/tap guards, process recreation, persistence failure, mode isolation and stale NFC reads. Source wiring tests preserve the existing camera/check-in/login flows and establish separate payment API routing.

Before signing/release, independently review the exact final commit and verify on a physical tablet with the matching staged backend: waiting → keypad → edit/confirm → customer QR; real wallet payment; fresh card payment; duplicate card; pending/review; expired QR and cancel/paid race; disconnect during create/tap; force-stop/restart; settings/re-login/recreation; explicit paid Finish → original inline check-in camera; no rewards for either funding route. No device was attached during development, so these are not claimed as completed.

Candidate version: 2.17.0 (50). Expected production asset: `rewards-nfc-display-2.17.0-production.apk`. Zapstore's prospective release_source must not be published until the parent has signed, created and verified that release asset. No signing, tag, push, publishing or production installation is authorized in this development task.
