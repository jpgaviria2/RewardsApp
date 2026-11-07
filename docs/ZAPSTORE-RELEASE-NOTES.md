# Rewards NFC Display 2.17.0

- Adds a separate staff Collect payment flow, without a PIN: enter the final CAD cents total already calculated by Square, confirm, and hand the tablet to the customer.
- Customer payment screen presents an immutable Lightning invoice QR and accepts compatible Boltcard taps through the authenticated store collection API only. These sales do not earn rewards; the matching server plugin must enforce exclusion.
- Persists only payment identity and dispatch guards before requests. Interrupted/ambiguous charges reconcile the same operation instead of creating or tapping again; pending and review block new charges.
- Requires server-authoritative paid/expired/cancelled results and explicit staff finish before rewards check-in resumes. Cancellation is followed by status reconciliation.
- Retains inline rewards camera, existing check-in, onboarding, settings and BTCPay login. Missing collection routes show upgrade/configuration guidance before amount entry.

Release candidate metadata only: parent release workflow must review the exact commit and publish `rewards-nfc-display-2.17.0-production.apk` before the prospective Zapstore release source is live. Signing, publishing, physical NFC/camera and staged server payment acceptance are not part of the development build gate.
