# Rewards NFC Display 2.13.0

- Adds standards-compliant LNURL-pay QR scanning to the existing Lightning-address rewards workflow.
- Strictly validates LNURL Bech32 payloads and decoded HTTPS destinations before submitting a check-in.
- Preserves scan evidence and displays a destination label derived locally from the scanned QR.
- Keeps the existing server-authoritative reward amount, confirmation, retry, cancellation, and terminal-status flow.
- Preserves Lightning-address scanning while canonicalizing its server-facing value for reliable correlation.
