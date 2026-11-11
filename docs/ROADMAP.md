# Rewards NFC Display — implementation and acceptance backlog

Baseline: `v2.22.0`, versionCode **55**, package `com.bitcoinrewards.nfcdisplay`.
Source baseline: `61bd7a3a4d6dd95dbf3945e2d38c907e41edc04d` on `main`.
This is the NFC/QR rewards kiosk, not the separate Trails Coffee wallet app.

## Already implemented — do not reimplement from stale branches

- QR and Boltcard NFC reward check-in through the existing guarded operation flow.
- Store/Login Code QR onboarding and authenticated, exact-store scanner admission.
- Trusted-display recovery, duplicate/admission guards and persisted legacy payment recovery.
- Native BTCPay POS discovery, explicit selection when multiple keypads exist, profile-bound manual fallback, and guarded Take payment navigation. This replaced new custom-invoice creation, not legacy recovery.
- Process-owned external HID scanner framing across Activities/dialogs (2.20.0).
- Confirmed Trails-handle parsing and finite check-in celebration (2.21.0).
- Simplified kiosk customer region, default external HID/NFC input, and opt-in built-in camera (2.22.0).

These source features are present on main. Distribution, companion-server compatibility and physical acceptance are different claims.

## P1 — paired backend integration and verified deployment

Android 2.22.0 advertises the presentation hint `TrailsRewardsKiosk/1`. Complete and verify the companion Bitcoin Rewards backend contract for:

- A single server-owned waiting/customer card rather than overlapping native/server instructions.
- Exact server-side Trails-handle presentation consistent with the confirmed check-in identity.
- Healthy no-op polling that does not repeatedly reload the full page or flash verification UI.

The Android release notes do not attest backend implementation or deployment. Inspect the actual deployed backend before coding: if these features already exist, record its immutable commit/version and acceptance evidence rather than duplicating them. A user-agent hint must never grant authorization. Retain current authenticated check-in routes, scoped customer identity and payment/recovery behavior.

Acceptance: a current kiosk displays the intended waiting card, idle polling causes no full-page refresh/green flash, and a confirmed check-in shows only the matching identity; verify session expiry, reconnect and restart without duplicate submission. Server work/deployment and any financial transaction need their own authorization.

## P2 — device acceptance and missing runtime coverage

These are verification/coverage tasks, not claims of missing app implementation:

- Same-certificate signed upgrade without uninstalling, clearing wallet/payment state or losing display configuration.
- Fresh launch, Store/Login Code QR setup, expired login, CookieManager reuse, profile changes and process restart.
- Physical HID input across real Activity/dialog focus, partial frames, pause/resume and interrupted scans. Offline replay does not prove Android delivery. External scanner submission requires API29+; older supported APIs are consume-only.
- Physical NFC plus QR fallback: repeated taps/scans, operation locks and stale callbacks while entering/leaving Take payment.
- Camera remains off after upgrade unless explicitly enabled; saved revocation takes effect when returning to an older display. Verify permission denial, re-enable, camera selection and lifecycle behavior.
- Native POS discovery with one/multiple/no keypads, denied store permission, expired session, changed/translated markup, TLS/network errors and stale async results after profile changes. Preserve fail-closed classification.
- Native checkout interruption/recreation, Back to rewards, legacy pending/review/unknown recovery and explicit terminal Finish. Back is not cancellation; no native-POS exactly-once or Android NFC-payment guarantee is implemented.
- Kiosk layout, confirmed feedback, idle privacy clearing and accessibility on the actual display hardware.

Add Android instrumentation tests for Activity/WebView/lifecycle behavior where a device/emulator is available; retain JVM/source/replay tests as complementary evidence. Record executed device/version/certificate/source identities and outcomes. Live invoices, rewards and payments require explicit approval; never manufacture terminal financial state to pass a test.

## P3 — optional hardening and product decisions

- HID cold-start attestation: process-local framing cannot attest a prefix emitted before the process observed input. Specify a delimiter/re-arm UX or device protocol before claiming complete-frame safety after process death; preserve discard-through-delimiter behavior for observed blocked prefixes.
- Replace the known-HTML POS discovery adapter only if a supported authenticated exact-store structured API becomes available. Do not weaken ownership/type/style/completeness checks for convenience; Greenfield authentication is not the existing WebView cookie.
- Independently approved CI/CD hardening: eligible human/CODEOWNER PR approval, release-environment human approval/no admin bypass, immutable release-tag rules and allowed-Actions/fork approval policy. Re-read live settings before changing them; `docs/CI-CD.md` contains a dated configuration snapshot, not proof of today's enforcement.
- Remove the temporary merchant-collection branch-name scope guard in a separately reviewed CI change if it is no longer useful. Keep required CI and exact-commit independent review gates.

These items are not authorization to change product behavior, credentials or repository protection.

## Integration and branch disposition

`v2.22.0` already points to main. PR #14 intentionally included the previously released 2.18/2.19 native-POS lineage; its source head `33549951b483ea88d6bda97e9a5f65a9a18595e8` has the same Git tree as squash merge `8f9ee15ca6a8fe4760b398a8468480fe474f344d`. PRs #15 and #16 then added 2.21/2.22 on main. Do not merge old PR #13 over the newer release merely to make its UI say merged: close it as superseded by #14.

Development branches can be removed after verifying their disposition and preserving original refs in a private verified Git bundle. Keep immutable release tags/artifacts. Squash-merged commits may not be main ancestors even when their exact source was integrated; record that distinction. Unmerged unique work must be preserved and documented, never silently discarded or blindly merged.

## References

- `ZAPSTORE-RELEASE-NOTES.md`: 2.22.0 behavior and companion-backend limitations.
- `HID-FRAMING.md`: process framing, mutation/replay evidence and cold-start limits.
- `POS-DISCOVERY.md`: exact-store authentication, HTML completeness and bounds.
- `MERCHANT-COLLECTION-ACCEPTANCE.md`: POS and retained legacy financial-state acceptance.
- `CI-CD.md`: required checks, source review, signing and separate publication boundaries.
