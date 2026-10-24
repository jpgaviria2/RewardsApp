# Rewards NFC Display 2.13.3

- Authenticates automatic inline scanner check-ins with the existing BTCPay WebView session cookie.
- Sends the cookie only to the configured trusted HTTPS BTCPay origin and never persists, logs, or passes it through an Intent.
- Pauses automatic scanning when the cookie is missing, BTCPay redirects to login, or BTCPay returns HTTP 401/403; HTTP 403 also explains that the account may be missing store-settings permission.
- Preserves the automatic inline camera, strict Lightning-address and LNURL-pay parsing, duplicate-scan throttling, camera Flip control, and BTCPay Login Code flow.
