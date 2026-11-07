# Android change prevention and reviewed release

## Boundaries

`Android CI` is unprivileged verification. It runs for every PR into `main`
(including forks) and every `main` push, without path filters. It uses hosted,
ephemeral Ubuntu runners, JDK 17, Android SDK 34/build-tools 34.0.0, a read-only
contents token, no persisted checkout credentials, no build cache and **no
repository/environment secrets**. There is no `pull_request_target`, privileged
`workflow_run`, arbitrary tag-push release, or PR-to-release artifact promotion.
All Actions are pinned to full commit IDs. Policy binaries are official release
assets pinned by version and SHA-256; updates require review of both.

Required checks (select their actual check names after the first PR run):

- **Policy and secrets**: temporary feature scope; actionlint syntax/expression
  checks; CI-script regression tests; Gitleaks history scan.
- **Clean Android gate**: `clean lintDebug testDebugUnitTest
  testReleaseUnitTest assembleDebug --no-daemon --no-build-cache --rerun-tasks`.
  XML reports must exist for **both** variants, each with at least one executed,
  non-skipped passing test and no failure/error. Missing tests are failure,
  never an acceptable `NO-SOURCE` result. Gradle failure is never masked by an
  artifact upload; a separate always-run XML check reports missing tests.

Reports, XML and debug APK are retained 14 days even after failure (if produced).
Sanitized secret finding locations are retained 7 days. Raw secret reports/logs
are temporary, never printed or uploaded. Gitleaks defaults are extended, not
replaced by homemade credential regexes. It scans all fetched reachable history,
not ignored workstation `.secrets/` or generated APKs. A single exact immutable
historical false-positive fingerprint is documented in
`scripts/ci/gitleaks-history.ignore`: a Java preference-key name at commit
`58ff52c80706b912da7649e3a881ef3ff626556b`, not a credential. Never add a real
credential to that file: revoke/rotate it and address history separately.

`feature-scope.py` is a temporary merchant-collection allowlist applied to PRs
whose head branch is `feature/merchant-collection`. It permits only the explicit
collection files already in the implementation, associated metadata/docs, and
CI paths. Deletions/renames cannot hide the old path (`--no-renames`). An
unrelated scanner/login/parser/settings edit fails. It is a review aid, **not**
a security boundary: a contributor can rename their branch or propose changes
to the allowlist. Review such changes as carefully as app code; remove/replace
the feature-specific check in a separately reviewed follow-up after merge.

## Mandatory repository settings (not changed by this patch)

1. Protect `main` with a ruleset/branch protection: PR-only updates, both checks
   above required, branch up-to-date before merge, conversations resolved,
   at least one independent approval, stale approvals dismissed on new commits,
   latest push independently approved, required CODEOWNER review, no force pushes
   or deletions, no bypass including administrators. Add an eligible independent
   reviewer/team if `@jpgaviria2` is also the author; self-review is not approval.
2. Protect `v*` tags from update/deletion. Limit tag creation to authorized release
   maintainers. Do not use a generic arbitrary-tag automatic publisher.
3. Actions default workflow permissions read-only, do not permit Actions to
   approve PRs, require approval of outside/fork contributors, restrict allowed
   Actions to the pinned dependencies used here. Do not expose signing or Nostr
   credentials as repository-wide secrets or on self-hosted PR runners.
4. Create **release-signing** and **release-publication** environments, both
   restricted to protected `main`, independent required reviewers, prevent
   self-review, disallow administrator bypass. Do this **before** installing
   signing secrets: a YAML environment name alone does not configure protection.
5. Require reviewers of `.github/`, `scripts/ci/`, wrapper/build tooling and
   release metadata. CODEOWNERS is included but has no enforcement without
   the settings above. Review dependency/build script changes because signing
   executes the reviewed Gradle build with the production signer available.

### Read-only inventory at implementation time

Inspected with `/opt/data/bin/gh` on 2026-11-07:

- Repository Actions secret names: **none**, API `total_count: 0`.
- Environments: **none**, API `total_count: 0` (therefore no environment secrets).
- `main` branch protection: API returned **404 Branch not protected**.
- No existing checked-in or registered Actions workflows were found initially.

These are honest setup blockers, not configured guarantees. No settings, secrets,
keys, tags, remote branches or releases were created/changed by this patch.
An organization-level secret inventory was not established (the authenticated
account lacks `read:org`); no organization secret availability is assumed.

Configure these **names only**, in `release-signing`, through an approved secret
channel, never chat/logs/commits:

| Secret/variable | Requirement |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` (secret) | Base64 of the **existing** production keystore, not a generated replacement |
| `REWARDS_NFC_KEYSTORE_PASSWORD` (secret) | Existing store password |
| `REWARDS_NFC_KEY_ALIAS` (secret) | Existing production alias |
| `REWARDS_NFC_KEY_PASSWORD` (secret) | Existing key password |
| `ANDROID_RELEASE_CERT_SHA256` (environment variable) | 64 hex characters independently verified against installed/previous production APK certificate |

All four secret names and the certificate variable are currently unconfigured.
The persistent workstation signing files remain untouched:
`.secrets/rewards-nfc-release-keystore.jks` and
`.secrets/rewards-nfc-release.properties`. **Never generate a new key.**
GitHub publication uses the built-in `GITHUB_TOKEN` only in its own job with
`contents: write` and `actions: read`; no PAT is requested. Zapstore Nostr signing
stays local and is deliberately **not** added to PR or GitHub release secrets.

## Reviewed GitHub release pipeline (manual, not run by this patch)

1. Finish the exact source/version/notes/Zapstore-manifest review, merge through
   required checks, and freeze the resulting current `main` SHA. Squash merging
   changes identity: approve and tag the **merge result**, not the old PR SHA.
   Complete physical tablet/staged-backend acceptance documented in
   `MERCHANT-COLLECTION-ACCEPTANCE.md`; JVM CI cannot prove hardware or money-flow
   safety. Any subsequent change requires fresh exact-commit review/acceptance.
2. With explicit release authorization, a maintainer creates the intended
   immutable `vX.Y.Z` tag on that reviewed current main commit. This workflow
   never creates/moves a tag. Verify remote tag identity before proceeding.
3. Dispatch `Reviewed release` **from main**, specifying the full lowercase
   40-character `reviewed_sha` and existing `tag`. The workflow checks out the
   trusted dispatch commit, **not** arbitrary supplied source. The source gate
   runs before secrets are injected and again before publication. It requires:
   - dispatch ref is `refs/heads/main`;
   - supplied SHA equals both checked-out trusted workflow source and current
     remote main tip (therefore also main ancestry);
   - existing tag resolves to exactly that SHA;
   - latest main-push `Android CI` run for that exact SHA succeeded;
   - tag equals app versionName and Zapstore release_source names the exact
     expected GitHub repository/tag/production APK.
4. `release-signing` reviewer confirms the exact SHA, acceptance and certificate.
   Signing reruns clean lint, both unit variants and `assembleRelease`, rejects
   missing secrets, verifies the APK signature with `apksigner`, and requires the
   independently established certificate hash. Private temporary keystore is
   removed on exit. Only the signed APK, SHA256 and source SHA leave the job;
   never the keystore/properties. Secrets are not available to policy/PR jobs.
5. A separate `release-publication` approval grants only that job write access.
   It downloads the artifact from this same run, rechecks source/main/tag/CI,
   source SHA and APK checksum; refuses any existing release; publishes APK and
   checksum without clobber; reads back non-draft/non-prerelease state, downloads
   public APK and compares bytes, and verifies latest-release discovery.
   A main advance between jobs fails closed: re-review/re-tag a new candidate,
   never silently move an immutable tag. A partial publish/readback failure is
   a blocker requiring explicit repair authorization, not an automatic overwrite.

## Zapstore promotion (separate, explicitly authorized local operation)

GitHub publication is **not** Zapstore publication. The pipeline deliberately
keeps the Nostr key out of GitHub until an audited unattended publisher and
protected environment are separately approved. Use the established local
`/opt/data/bin/zsp` and existing `.secrets/zapstore-nostr.json`; never generate a
new signer, print `nsec`, or place it in command-line arguments. Local zsp was
`v2.0.0`; check the official latest release and migrate/verify a newer version
before an actual publication rather than assuming this version stays current.

After explicit authorization and verified GitHub publication:

1. Download the signed artifact from that exact workflow/run (not a PR artifact).
   Check its `SOURCE_COMMIT`, tag/main ancestry, certificate and checksum and
   compare it byte-for-byte with the public GitHub APK.
2. Load the existing Nostr signer into `SIGN_WITH` only inside a subprocess
   environment. Capture output privately, redact signer material defensively,
   and do not enable shell tracing or upload raw publisher logs.
3. Run `zsp check --json zapstore.yaml` with that signer. Require
   `authorized: true` and an active NIP-C1 proof for the exact APK certificate.
   Missing proof is a blocker; no proof-bypass flag or new Android key.
4. Publish using the same retained APK as local source so it uploads to Zapstore
   CDN, with canonical GitHub metadata and exact commit. With verified zsp 2.x:
   `zsp publish -q --skip-preview --commit <reviewed-sha>
   -s <retained-production.apk> zapstore.yaml`. Keep checked-in `release_source`
   pointing at the canonical GitHub release. Do **not** use `--overwrite-release`
   except an explicitly authorized repair of an already-published version.
5. Read back `wss://relay.zapstore.dev`: app kind 32267 for the package, release
   kind 30063 for `com.bitcoinrewards.nfcdisplay@<version>`, then its referenced
   asset kind 3063. Require exact version/code, package, certificate hash, size,
   APK SHA256 and every advertised URL (including CDN). Download each advertised
   APK and compare to retained bytes. Relay `OK`/CLI exit zero/local cache/app
   page alone do **not** verify publication; absent events/404/mismatch remain
   blockers. Record event IDs only after this readback.

There is intentionally no automatic Zapstore publisher or claimed publication
result here. This local promotion is the remaining authorized release stage,
not a hidden fallback that runs on PR/tag/push.

## Local verification

```sh
export JAVA_HOME=/opt/data/toolchains/java/jdk-17.0.16+8
export ANDROID_HOME=/opt/data/android-sdk ANDROID_SDK_ROOT=/opt/data/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"
bash scripts/ci/android-check.sh
bash scripts/ci/install-tool.sh actionlint "$TMPDIR/rewards-ci-bin"
bash scripts/ci/install-tool.sh gitleaks "$TMPDIR/rewards-ci-bin"
export PATH="$TMPDIR/rewards-ci-bin:$PATH"
actionlint -shellcheck= .github/workflows/*.yml
python3 scripts/ci/test-ci.py
bash scripts/ci/secret-scan.sh "$TMPDIR/rewards-secret-findings"
python3 scripts/ci/feature-scope.py origin/main HEAD
git diff --check
```

`test-ci.py` uses isolated scratch Git/XML/CLI fixtures (no production credentials
or external writes), covers passing/failing/missing/zero/skipped test reports,
feature allow/reject cases, malformed source inputs, non-main dispatch, exact
main/tag/version/CI release gates, missing signing secrets and real Gitleaks
finding sanitization. Fixture CI responses are test doubles, **not** a claim
that GitHub Actions ran. Signed build/publication require configured credentials
and human authorization and are not exercised by these tests.
