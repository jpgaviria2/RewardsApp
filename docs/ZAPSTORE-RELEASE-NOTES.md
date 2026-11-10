# Rewards NFC Display 2.21.0 (version code 54)

- **Scan here:** a prominent scanner prompt replaces the idle scan animation while preserving the customer tutorial and existing NFC, camera, and Bluetooth HID entry points.
- **Confirmed check-in feedback:** a finite confetti effect and confirmation appear only after a validated successful check-in and local capture. A bounded generated or custom handle from the confirmed `@trailscoffee.com` response is shown; other destinations receive generic confirmation. Raw scan content is not used as the confirmed greeting.
- Personal confirmation clears on idle, interruption, pause, and destruction. Late or retired callbacks cannot replace the current customer's presentation; pending nonpersonal display reconciliation resumes only when scanning is eligible. Duplicate confirmation is guarded without a lifetime kiosk capacity limit.
- Existing financial-operation locks, payment/recovery behavior, process-owned HID framing, and authentication gates remain unchanged. No server/plugin API change is required.
- Stable GitHub distribution and Zapstore main-channel metadata are intended; a source URL alone does not establish signing, publication, or catalog availability. These are independently verified release stages.
- **User acceptance remains separate:** native Android layout/accessibility/lifecycle, physical scanner/NFC/camera behavior, production installation, and live check-in confirmation are not established by offline tests or model review. No automated production check-in, payment, reward, or fund-moving test is performed.

## Retained 2.20.0 Bluetooth HID behavior

- Supported external keyboard-style scanner vendor/product `045E:0040` on Android API 29+ supplies Lightning addresses and standards-compliant LNURL-pay scans through the existing customer check-in flow. Other keyboards are not treated as supported scanners.
- Disabled, blocked, or interrupted scans are consumed/discarded rather than forwarding partial payloads. On API 19–28 the supported scanner is guarded/consume-only; unavailable external-device APIs are not called.
- The debug-only offline HID harness remains in its separate `.hidtest` application ID. An offline diagnostic is not a production artifact or live check-in acceptance.

## Retained 2.19.0 native POS behavior

- **Take payment needs no POS URL typing.** The app uses your existing BTCPay login session to discover active apps belonging to the configured Store ID. One suitable Keypad Point of Sale opens automatically; multiple keypads show their names and app IDs for explicit selection. Other POS styles and unrelated apps are never picked as a substitute.
- Discovery reads BTCPay's cookie-authenticated exact-store apps page, not the API-key-only Greenfield apps API. It makes one bounded HTTPS request to the configured origin, never follows redirects with cookies, and does not create an invoice, collect API keys, or enumerate other stores.
- Discovery cancels on pause/destruction and rejects late results or selections after lifecycle/profile changes. A missing login, store permission rejection, missing route and unsupported markup receive separate guidance. The optional profile-bound manual POS setting remains an explicit fallback, not the normal workflow.
- Retains Back to rewards, native BTCPay Login Code scanning, same-origin WebView/TLS restrictions, camera/NFC/HCE suspension in payment mode, and all existing custom-payment recovery/history and unresolved-operation locks. Back still does not cancel a BTCPay invoice.

No server/plugin changes, invoice creation or fund-moving tests were performed. Physical signed upgrade, authenticated tablet discovery, checkout/return, login/session, lifecycle and NFC/camera acceptance remain device-only checks.
