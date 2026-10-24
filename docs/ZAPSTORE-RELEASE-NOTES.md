# Rewards NFC Display 2.13.4

- Automatically retries the exact configured rewards display after transient main-frame network or TLS failures, using bounded exponential backoff.
- Cancels stale retries after a successful display load or when the activity is destroyed.
- Never bypasses Android TLS validation and never replays a consume-once BTCPay Login Code URL.
- Keeps the inline customer scanner hidden until the trusted rewards display completes successfully.
- Continues rejecting fixed BOLT11 invoices and now explains that rewards require a reusable Lightning address or LNURL-pay QR.
