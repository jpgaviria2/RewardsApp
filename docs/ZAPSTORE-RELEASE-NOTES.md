# Rewards NFC Display v2.9.6

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

- Uses the BTCPay 2.4-compatible `/login?loginCode=...` endpoint for login-code exchange.

- Scan BTCPay Login QR now logs in immediately after scanning, matching BTCPay login-code behavior.

- Hide the Scan Rewards Profile overlay on BTCPay login/account pages; only show it on Bitcoin Rewards display/check-in pages.

- Persist BTCPay login-code session cookies and inject them into the WebView before opening the rewards display, preventing a second BTCPay login screen after authentication.

- If a login-code session is missing or BTCPay redirects back to login, return to app settings for native QR login instead of showing BTCPay web camera/login pages.
