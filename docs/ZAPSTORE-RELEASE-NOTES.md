# Rewards NFC Display 2.14.1

- Fixes an immediate launch crash caused by assigning a background drawable directly to Android `TextureView`.
- Keeps the tutorial poster as a separate image behind the video surface, preserving the visual fallback without using an unsupported `TextureView` operation.
- Rebuilds the customer screen around a clear “Get rewarded in bitcoin” experience.
- Bundles an offline example-wallet video that shows customers how to open and present their rewards QR.
- Shows the live camera and Boltcard tap instructions together, with larger visual targets and customer-friendly feedback.
- Replaces long Lightning addresses and LNURL strings with a short wallet name or a generic wallet/card label.
- Adds animated connecting and wallet-ready states without claiming a reward was paid before the sale completes.
- Preserves authenticated Boltcard submission, strict QR parsing, TLS validation, QR fallback, and the 30-attempt BTCPay/StartOS restart recovery.
