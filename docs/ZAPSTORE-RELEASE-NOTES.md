# Rewards NFC Display v2.10.1

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

- Scan BTCPay Login QR now saves and launches the display automatically after a successful login; no separate Save & Launch tap is required.

- Store the full BTCPay Set-Cookie lines and replay them into the WebView to keep the login-code session active when launching display.

- Remove the manual Login with BTCPay Code button from setup; the native QR scan now owns the login flow.

- Treat HTTP 200 from BTCPay login-code exchange as not authenticated instead of saving a partial login-page cookie; only redirect responses with session cookies are accepted.

- Add login-flow diagnostics for session cookie replay and BTCPay login redirects.

- Switch setup to BTCPay Server’s own web login-code scanner inside the WebView, with camera permission enabled.

- Open BTCPay login with a rewards-display return URL so successful web login returns to the kiosk display.

- Keep customer profile scan overlays hidden on BTCPay login pages while allowing the BTCPay web scanner to use camera access.

- Rewards profile scanner now defaults to the front camera for customer-facing countertop use.

- Add a Flip Camera control to switch between front and back cameras while scanning customer Lightning-address QR codes.

- Automatically recover the WebView after screen lock/unlock or temporary DNS/network errors by reapplying cookies and retrying the rewards display.

- Hide customer scan controls while reconnecting so Android WebView error pages do not leave kiosk controls over a failed page.
