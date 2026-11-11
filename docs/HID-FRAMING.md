# Process-owned HID frame authority (introduced in 2.20.0 / code 53)

Integration status: the accepted successor was merged through PR #14 into main and released as 2.20.0. Main now contains 2.22.0 (55). The rejected tree below remains rejected; historical unsigned-candidate evidence is not physical production acceptance. See `ROADMAP.md` for outstanding work.

The rejected tree `42d45ca4119034ee5213a21787962b4558d24c90` allocated an independent `HidScannerInput` (and framer) in each Activity. Offline reproduction using its real field initializers and dispatch bodies showed Settings consuming `alice.` while disabled, followed by enabled Main accepting `bob@example.com` plus Enter as a different valid destination.

`HidScannerInput.processInput()` now owns the adapter and framer for the lifetime of the app process. All six manifest Activities, all app-owned dialog key listeners (via their Activity field), and the debug diagnostic use that same instance. It stores framing/source data only: no Activity, Context, View, payload listener or financial coordinator. Listeners remain call-local. Calls originate from foreground UI dispatch; this is not a global/background keyboard hook. Tests can still construct isolated adapters.

The existing Main/profile/debug pause resets now invalidate the same process frame. Reset preserves a blocked prefix even if there were no buffered characters. Disabled or unattested scanner events remain consume-only without Unicode reads. Delimiters recover the next complete frame. Source/device changes, timeout, repeat, invalid actions and operation locks retain the existing whole-frame discard policy. `getUnicodeChar(event.getMetaState())`, device admission, parser, financial callbacks and signing/release policies are unchanged.

## Reproduction and controls

Run `python3 scripts/ci/test-hid-replay.py` with JDK17 available. It compiles the actual adapter, framer and wallet parser, exact Activity field initializer expressions, dispatch bodies and dialog listener expressions. It source-extracts the Main/profile `hidScanner.reset();` pause expression; it does **not** run full Android lifecycle methods. The callback seam records parsed destinations; it never invokes a financial endpoint. Getter doubles label its limits: no real Activity launch, Android focus routing, physical key map or device delivery is established.

The replay retains the earlier single-adapter assertions and adds cross-Activity blocked-prefix/recovery, enabled/disabled/enabled round trips, repeated pause invalidation, blocked delimiter recovery, six-Activity owner identity/source-UP/external-attestation cases, all four dialog prefixes and debug initializer ownership. `test-hid-process-owner-mutations.py` independently restores each of the seven old constructors in a disposable source copy and requires a specific behavioral/ownership failure. Existing blocked-prefix mutations remain required.

The diagnostic isolation test now requires the shared getter instead of a private constructor; this deliberately changes only its ownership expectation, not isolation, parsing, permission or no-submission assertions. Existing Java unit tests and established replay acceptance assertions are retained unchanged.

## Explicit boundaries

This owner is process-local, not durable. Process death loses framing state. Cold initialization cannot know a prefix emitted before the app observed any scanner event, and no background capture is introduced to learn it. The first foreground frame after such an unobserved interval cannot be attested as whole by this design; use an observed delimiter and a fresh complete scan. This repair claims cross-Activity safety for observed input within a live process, not hardware restart/protocol authentication. Separate app processes (including the diagnostic application ID) do not share memory.

No physical scanner, signed upgrade, live check-in, financial callback, install or publication is validated by offline replay. The unsigned diagnostic APK is a separate artifact and must not replace provenance for a previously installed diagnostic. The accepted successor subsequently passed the release process and was merged/published as 2.20.0; that does not promote the old rejected tree or establish the device-only acceptance listed in `ROADMAP.md`. New source changes still require fresh exact-source review before signing/publication.
