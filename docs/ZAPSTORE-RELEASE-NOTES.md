# Rewards NFC Display 2.13.5

- Restores the longer restart-recovery window that previously kept kiosk displays alive through BTCPay and StartOS service restarts.
- Retries temporary HTTP 408, 425, 429, 500, 502, 503, and 504 responses as well as main-frame network and TLS failures.
- Uses bounded backoff for up to 30 attempts, reaching a 30-second interval instead of stopping after roughly 23 seconds.
- Keeps the scanner and failed Chromium page hidden while reconnecting to the exact configured rewards display.
- Never retries authentication, permission, or not-found responses; bypasses TLS validation; or replays a consume-once Login Code URL.
