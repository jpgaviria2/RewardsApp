# Rewards NFC Display 2.13.1

- Restores native scanning of short-lived BTCPay Login Codes for display authentication.
- Accepts BTCPay login URL QR payloads and the legacy `code;baseUrl` format while requiring HTTPS and the configured server origin.
- Never stores the one-time login code and ignores QR-provided return targets; the rewards display URL is constructed locally from the trusted server and Store ID.
- Separates display readiness from optional scoped rewards-scanner provisioning, so the display can launch and authenticate without scanner credentials.
- Preserves the WebView cookie session and replaces destructive legacy cleanup with migration of only the non-secret BTCPay URL and Store ID.
- Keeps rewards-scanner device tokens isolated in Android secure storage and prompts for provisioning only when scanner functions are opened.
