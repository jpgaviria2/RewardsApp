# Rewards NFC Display 2.16.1

- Accepts BTCPay's native raw Store ID QR as well as the canonical Bitcoin Rewards display QR.
- Uses the immediately following fresh BTCPay login QR to establish and validate the trusted HTTPS server origin for raw Store IDs.
- Keeps the camera scanner and NFC reader disabled until the protected rewards check-in API is installed, reachable, and authorized for the current BTCPay session.
- Keeps pending Store configuration unsaved until both the trusted display and protected check-in capability verification succeed.
- Distinguishes an expired login from a missing or disabled Bitcoin Rewards server endpoint.
- Scrubs the one-time login exchange from WebView history before capability verification and preserves confirmed non-secret Store setup through activity recreation.
