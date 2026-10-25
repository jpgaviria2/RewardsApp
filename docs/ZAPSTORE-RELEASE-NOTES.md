# Rewards NFC Display 2.15.0

- Gives the camera scanner the full screen width and a substantially larger live preview.
- Moves the example-wallet tutorial into the lower display area and shows it only while waiting for a customer check-in.
- Automatically replaces the tutorial with the checked-in or reward-ready display as soon as the server reports that state.
- Uses customer-facing “sats” language instead of “bitcoin” language throughout the native rewards experience.
- Keeps foreground NFC reader interception active during check-in confirmation so Boltcard taps do not open or prompt for Glow, Fountain, or another wallet app; duplicate submissions remain blocked.
- Preserves authenticated Boltcard submission, strict QR parsing, TLS validation, QR fallback, and the 30-attempt BTCPay/StartOS restart recovery.
