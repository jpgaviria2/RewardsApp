# Rewards NFC Display 2.18.0

- Prominent Customer rewards and Take payment staff actions on the main screen.
- Take payment opens the merchant's existing native BTCPay POS in a dedicated in-app WebView with Back to rewards. Configure the optional HTTPS `/apps/APP_ID/pos` URL in Settings, then Save & launch display; no merchant URL is shipped as a default. The URL is bound to the configured server and Store ID.
- Replaces new custom payment creation. Existing custom PaymentOperationStore operations and history remain recoverable with their original durable identity. Unresolved operations continue to block rewards and native POS until reconciled and explicitly finished.
- Stops rewards camera/NFC capture and clears stale HCE rewards payloads before POS launch. Native POS does not add Android NFC payment functionality.
- Preserves native BTCPay Login Code flow and CookieManager sessions, HTTPS same-origin navigation, TLS validation, NFC/rewards capture and scanner recovery. POS denies cross-origin resources, external intents, file/content access and WebView permission requests.

No server changes or fund-moving acceptance tests. Physical tablet upgrade, camera/NFC ownership, merchant POS checkout and return-to-rewards acceptance remain device-only checks.
