# Rewards NFC Display 2.13.2

- Restores the automatic inline customer rewards camera above the trusted rewards display and check-in pages.
- Starts the camera without a manual scanner/provisioning button, pauses it on login, other pages, and app backgrounding, and retains the front/back Flip control.
- Sends both strict Lightning-address and LNURL-pay scans through the deployed legacy check-in endpoint using the same safe parser and local status labels.
- Preserves duplicate-scan throttling and the BTCPay Login Code WebView authentication flow introduced in 2.13.1.
- Keeps the separately authenticated secure scanner provisioning available only in Settings for future scoped workflows.
- LNURL-pay scans are prepared identically to Lightning addresses; servers without the matching plugin upgrade may reject LNURL-pay with a clear local status.
