# Rewards NFC Display 2.16.0

- Adds QR-only kiosk onboarding: scan the canonical Bitcoin Rewards Store QR, confirm the detected BTCPay server and Store ID, then scan a fresh BTCPay login QR.
- Removes the need to type the Store ID or login code during the QR setup path while preserving manual server and Store ID entry as a fallback.
- Rejects malformed, non-HTTPS, foreign-route, credential-bearing, query-bearing, fragmented, or non-canonical Store QR payloads.
- Keeps the one-time BTCPay login URL only in consume-once process memory and constructs the exact rewards display destination locally.
- Saves the scanned Store configuration only after the authenticated trusted display loads successfully; cancellation, rejection, or failed login leaves the existing configuration unchanged.
- Preserves pending non-secret setup state across activity recreation and invalidates scoped scanner metadata only after a successful store change.
