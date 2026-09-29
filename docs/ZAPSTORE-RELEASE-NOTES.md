# Rewards NFC Display 2.12.2

- Fixes BTCPay login-code flow so after authentication the kiosk returns to the configured Bitcoin Rewards display instead of landing on the BTCPay store dashboard.
- Keeps the app locked to `/plugins/bitcoin-rewards/{storeId}/display` after successful login while still allowing the BTCPay login page during authentication.
- Preserves the v2.12.1 scanner/display layout and Trails-style customer claim UI.
