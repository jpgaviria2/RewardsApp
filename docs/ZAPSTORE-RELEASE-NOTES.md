# Rewards NFC Display 2.22.0 (version code 55)

- Simplified native kiosk: one server-owned customer region, no stacked native Scan here/tutorial/NFC tiles, and the guarded Take payment action at the bottom. Confirmed check-in feedback and finite confetti replace the same customer region; personal text clears on interruption and idle.
- External supported HID scanner and NFC are the default. Built-in camera check-in is optional and off by default, including after upgrade. Enable it in Settings and choose Save & launch display. Returning to an older display honors saved camera revocation; disabled mode does not discover/request/start/decode the camera or grant WebView camera access. Store/login QR setup is unchanged.
- Preserves exact confirmed Trails-handle parsing, response capture/authentication, process-owned HID framing, financial-operation locks, payment/recovery and existing server routes.

## Companion backend requirement

The complete simplified server waiting card, exact server-side Trails-handle presentation and healthy no-op polling (removal of periodic full-page refresh/green verification flashes) require the companion Bitcoin Rewards backend update. Backend deployment is not included or attested by this Android release. On the unchanged server, existing authenticated check-in and payment contracts remain supported, but legacy server text/custom templates and timed refresh remain. Installing this APK alone does not fix that server-owned refresh behavior. The `TrailsRewardsKiosk/1` user-agent is a presentation hint only, not an authorization credential.

## Distribution and acceptance

Stable GitHub distribution with Zapstore main-channel metadata is intended; metadata does not prove Zapstore publication. Source approval, deterministic CI, protected production signing, GitHub publication, companion backend deployment and physical acceptance are distinct stages. Native device layout/accessibility, signed upgrade, physical HID/NFC/optional camera and production check-in/payment acceptance require separate hands-on confirmation. No automated production transaction, install, backend deployment or Zapstore publication is performed by this release worker.
