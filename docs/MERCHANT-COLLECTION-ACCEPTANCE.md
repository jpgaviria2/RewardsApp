# Native POS and legacy collection acceptance — 2.19.0 (52)

## Current release behavior

The main page exposes Customer rewards and Take payment. No POS URL typing is required: Take payment reads the configured store's cookie-authenticated `/stores/{storeId}/apps` page. Exactly one active Keypad POS opens automatically; multiple suitable apps require named selection (with IDs to distinguish duplicate names). Ownership links must match the exact configured store, POS settings links must match the same app and origin, and the rendered style must be Keypad. Cart/catalog/other apps are not arbitrary substitutes. Requests are bounded to one HTTPS GET, 512 KiB, 100 app rows and explicit timeouts with redirects disabled. Unsupported markup, missing session, store permission denial and unavailable routes fail closed with retry/settings guidance. Async results and selections are lifecycle-generation and origin/store bound, cancelled on pause/destruction and rechecked before opening POS.

The optional same-server HTTPS `/apps/APP_ID/pos` Settings value remains an explicit manual fallback after failed/empty discovery, never a built-in merchant default. Changing origin or Store ID erases that old binding and clears inherited input; scanner Save validates inputs before changing the display profile. A keystore write failure remains fail-closed and can leave display settings saved with scanner provisioning disabled.

Take payment opens the existing native BTCPay POS in a dedicated WebView, not the former Android custom invoice keypad. CookieManager owns the login session; no login code, cookie or WebView state is serialized by this Activity. File/content access, mixed content, external navigation and WebView permission requests are denied. Android native NFC payment functionality is not added. Rewards scanner/camera/reader ownership stops before launch, stale reads are generation-gated, and old HCE payload is cleared.

Back to rewards does not cancel native invoices. The POS screen always cautions staff to check previous checkout status before charging again. Recreation shows an additional interrupted-checkout warning and requires explicit reopening. Native BTCPay owns its checkout lifecycle; the retained custom-operation lock is not a native-POS exactly-once guarantee.

## Retained legacy custom-operation recovery

New custom creation UI is removed. The old `/plugins/bitcoin-rewards/{storeId}/pos/api` transport and PaymentOperationStore remain for already outstanding operations and history only:

- Original persisted operation UUID, origin/store, CAD amount, expected check-in identity, bound invoice and tap-dispatch guard remain authoritative. GET `/invoices/{operationId}` recovers the same reference; Android does not recreate it.
- Pending/review/unknown/auth/network failures continue to block native POS and rewards. An unknown operation is not silently forgotten. Back cannot bypass the active lock.
- Existing card handling preserves durable one-attempt dispatch; tap/cancel/reconcile ACKs are followed by GET. No ACK is settlement evidence.
- Explicit reason-bearing reconciliation sends the original amount and exact optional check-in identity to the original origin/store; no UUID-only replacement path or fabricated cancellation is introduced. A missing/contradictory GET remains locked.
- Responses validate operation, integer CAD amount, currency, invoice identity and status. Only a validated terminal response plus explicit staff Finish archives the reference; failed persistence restores the lock.
- Previous custom payments / recovery opens history. Selecting a historical reference reactivates the same operation for authenticated read/reconciliation and blocks replacement if uncertain again. Labels are status snapshots, not live settlement proof.
- Matching check-in release remains scoped to the captured identity, never cancel-all or an unrelated customer. Android copy is not proof of backend reward exclusion or settlement fencing.

The existing server must retain these legacy recovery routes. No server implementation/deployment or native POS accounting change is part of this release.

## Automated evidence and remaining device acceptance

Require a fresh clean pinned-wrapper strict-dependency Gradle gate: both lints, nonzero passing debug/release JVM tests and debug assembly, then independent read-only review of the exact candidate. GitHub CI must test the exact PR head and retain `tested-source.txt`, reports and APK. Verify production signer continuity, package/version/hash and public GitHub/Zapstore readback separately. JVM transport/state fixtures and source-wiring checks do not prove Activity/WebView or hardware execution.

Device-only acceptance remains outstanding when no device is attached:

1. Fresh and upgrade launch, settings, Store/Login QR flows, CookieManager session reuse, expired login and process restart.
2. Configure POS for profile A; switch to B through display/scanner/login paths; verify no inherited URL or A→B→A resurrection. Test scanner validation/storage failures and explicit replacement URL.
3. With the POS URL field empty, Take payment automatically opens a single active Keypad POS for this store. With multiple keypads, select by name/ID, never an arbitrary first entry. Verify no-keypad, expired login, insufficient store permission, transient network failure, unsupported markup and retry. Pause during discovery, change profile, return A→B→A and confirm old responses/selections cannot open the wrong POS. Confirm the optional manual fallback is explicit. Verify actual tablet layout and trusted navigation/TLS failure behavior without creating an invoice.
4. Verify rewards camera/NFC stop while POS is foreground and stale HCE/queued reads cannot broadcast/check in; Back resumes trusted-display authorization and original scanning.
5. Recreate/background/force-stop/reenter POS: interrupted-checkout guidance appears as designed; Back and history do not imply cancellation. Do not create a replacement charge merely because a page was closed.
6. Upgrade with an already outstanding old custom operation; verify recovery/unknown locks, settings/login, history and explicit validated terminal Finish. No test may manufacture or silently clear a real financial operation.

No fund-moving tests are authorized. Even an unfunded invoice is a server-side write and is outside this task's read-only merchant inspection. Obtain separate authorization before invoice creation or payment acceptance. Public release availability is not physical acceptance or an end-to-end accounting verdict.

## Distribution and repair

Version 2.19.0 (52), asset `rewards-nfc-display-2.19.0-production.apk`, feature-branch/tag public distribution for user testing. Main merge is not authorized. Existing main-only trusted release automation remains unchanged; local production signing requires exact-head CI and independent review evidence. Retain release checksum, certificate, source and relay/CDN receipts in the durable worklog.

If a published test build is faulty, mark the GitHub release with an install warning and communicate the affected version. Preserve immutable tags/artifacts and old payment state. Correct through a reviewed higher-versionCode, same-certificate forward release; do not force-replace public bytes, wipe identities or promise an ordinary in-place downgrade.
