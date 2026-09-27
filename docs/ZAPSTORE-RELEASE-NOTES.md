# Rewards NFC Display v2.9.1

Clean standalone RewardsApp production release.

- Keeps the Android app in the separate `RewardsApp` repository.
- Bumps version above the old v2.8.0 app so existing installs can update normally.
- Removes hard-coded signing passwords from Gradle.
- Adds production launcher icon and round icon.
- Adds Zapstore metadata/config and store-ready promotional assets.
- Sets cleartext HTTP traffic off.
- Marks NFC/HCE hardware optional so the app can install on tablets without NFC support.
- Adds native customer Lightning-address QR scanning.
- Adds native BTCPay login QR scanning.
- Adds BTCPay login-code onboarding in settings.
- Preserves NFC display/tap support when hardware is available.

- Store ID is optional for BTCPay login-code setup; the app auto-detects stores from the BTCPay session when possible.
