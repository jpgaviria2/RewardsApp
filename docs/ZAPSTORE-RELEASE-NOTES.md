# Rewards NFC Display 2.13.6

- Adds NFC reader-mode check-in for standards-compliant NTAG424 Boltcards.
- Reads the card's bounded dynamic `lnurlw` NDEF URI and submits it only to the authenticated same-origin Bitcoin Rewards endpoint.
- Keeps Lightning-address and LNURL-pay QR scanning available as the fallback customer check-in flow.
- Disables NFC during login, recovery, submission, pause, and destruction while preserving the 30-attempt BTCPay/StartOS restart-recovery behavior from 2.13.5.
- Never contacts the card provider directly, invokes an LNURL-withdraw callback, stores card keys, bypasses TLS validation, or replays a consume-once BTCPay Login Code URL.
