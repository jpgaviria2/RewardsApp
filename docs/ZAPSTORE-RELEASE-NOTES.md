# Rewards NFC Display 2.19.0

- **Take payment needs no POS URL typing.** The app uses your existing BTCPay login session to discover active apps belonging to the configured Store ID. One suitable Keypad Point of Sale opens automatically; multiple keypads show their names and app IDs for explicit selection. Other POS styles and unrelated apps are never picked as a substitute.
- Discovery reads BTCPay's cookie-authenticated exact-store apps page, not the API-key-only Greenfield apps API. It makes one bounded HTTPS request to the configured origin, never follows redirects with cookies, and does not create an invoice, collect API keys, or enumerate other stores.
- Discovery cancels on pause/destruction and rejects late results or selections after lifecycle/profile changes. A missing login, store permission rejection, missing route and unsupported markup receive separate guidance. The optional profile-bound manual POS setting remains an explicit fallback, not the normal workflow.
- Retains Back to rewards, native BTCPay Login Code scanning, same-origin WebView/TLS restrictions, camera/NFC/HCE suspension in payment mode, and all existing custom-payment recovery/history and unresolved-operation locks. Back still does not cancel a BTCPay invoice.

No server/plugin changes, invoice creation or fund-moving tests were performed. Physical signed upgrade, authenticated tablet discovery, checkout/return, login/session, lifecycle and NFC/camera acceptance remain device-only checks.
