# Rewards NFC Display 2.20.0 (version code 53)

- **Bluetooth HID scanner support:** supported external keyboard-style scanner vendor/product `045E:0040` on Android API 29+ can supply Lightning addresses and standards-compliant LNURL-pay scans through the existing customer check-in flow. Other keyboards are not treated as supported scanners.
- Scanning is admitted only on an eligible customer check-in screen. Disabled, blocked or interrupted scans are consumed/discarded rather than forwarding a partial wallet payload. On API 19–28 the supported scanner is guarded/consume-only; submission is not enabled and no unavailable external-device API is called.
- This successor retains the approved offline debug-only HID harness in its separate `.hidtest` application ID. The prior physical offline HID diagnostic passed; that diagnostic is not the production app or proof of live customer check-in acceptance.
- **Acceptance pending user testing:** production-signed upgrade and actual live Bluetooth check-in-screen tests, including eligible check-in, blocked-screen/lifecycle interruption and next-complete-scan recovery. Local replay/unit/lint evidence and the offline diagnostic do not replace these tests. No financial acceptance, payment, reward or fund-moving test is claimed.
- Distribution intent is a stable GitHub release and Zapstore main channel. The configured canonical release-source URL is reserved metadata, not evidence that an APK has been signed, uploaded or published.
- No server/plugin or app API contract changes are required by this HID successor.

## Retained 2.19.0 native POS behavior

- **Take payment needs no POS URL typing.** The app uses your existing BTCPay login session to discover active apps belonging to the configured Store ID. One suitable Keypad Point of Sale opens automatically; multiple keypads show their names and app IDs for explicit selection. Other POS styles and unrelated apps are never picked as a substitute.
- Discovery reads BTCPay's cookie-authenticated exact-store apps page, not the API-key-only Greenfield apps API. It makes one bounded HTTPS request to the configured origin, never follows redirects with cookies, and does not create an invoice, collect API keys, or enumerate other stores.
- Discovery cancels on pause/destruction and rejects late results or selections after lifecycle/profile changes. A missing login, store permission rejection, missing route and unsupported markup receive separate guidance. The optional profile-bound manual POS setting remains an explicit fallback, not the normal workflow.
- Retains Back to rewards, native BTCPay Login Code scanning, same-origin WebView/TLS restrictions, camera/NFC/HCE suspension in payment mode, and all existing custom-payment recovery/history and unresolved-operation locks. Back still does not cancel a BTCPay invoice.

No server/plugin changes, invoice creation or fund-moving tests were performed. Physical signed upgrade, authenticated tablet discovery, checkout/return, login/session, lifecycle and NFC/camera acceptance remain device-only checks.
