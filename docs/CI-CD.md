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

Required contexts on `main` (live snapshot below):

- **Policy and secrets**: temporary feature scope; actionlint syntax/expression
  checks; CI-script regression tests; Gitleaks history scan.
- **Clean Android gate**: `clean lintDebug lintRelease testDebugUnitTest
  testReleaseUnitTest assembleDebug --no-daemon --no-build-cache --rerun-tasks
  --dependency-verification strict`. Wrapper preflight and an isolated real Gradle
  checksum-negative fixture run before this clean gate.
  XML reports must exist for **both** variants, each with at least one executed,
  non-skipped passing test and no failure/error. Missing tests are failure,
  never an acceptable `NO-SOURCE` result. Gradle failure is never masked by an
  artifact upload; a separate always-run XML check reports missing tests.

- **Independent OpenCode review**: externally posted exact-commit status, not a
  self-approving Actions job. The reviewing operator posts success only after
  actual independent review of the final source, and verifies merge-tree identity
  before carrying evidence onto a merge commit. A new source change invalidates
  that evidence. Missing, pending, error, or failed status blocks release. This
  is model review, **not an independent human approval**.

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

## Live configuration snapshot and remaining hardening

Read back with `/opt/data/bin/gh` on **2026-11-07** after the coordinating
operator's authorized configuration (this build-integrity patch performs no
remote writes):

- `main` requires a PR, strict/up-to-date **Policy and secrets**, **Clean Android
  gate**, and **Independent OpenCode review** contexts; administrator enforcement
  and conversation resolution are enabled. Force pushes/deletions are disabled.
- Human approving-review count is **0**; CODEOWNER review and latest-push human
  approval are **not required**. No eligible second human account was configured.
  Stale-review dismissal alone is not an independent approval guarantee.
- `release-signing` and `release-publication` exist with protected-branch-only
  deployment policy. YAML further restricts execution to `main`. They have **no
  required human reviewer**, and `can_admins_bypass` is **true**. The job/environment
  names do not mean human release approval is enforced. A trusted-main manual
  dispatch can proceed once the source/CI/external-review gates pass.
- `release-signing` has exactly the following four secret **names** and one
  certificate variable. API inspection verifies existence, not secret values.
  The coordinating operator reports uploading the preserved matching production
  signer; this patch never reads/decrypts it or signs an APK.

| Secret/variable | Requirement |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` (secret) | Existing production keystore, never a generated replacement |
| `REWARDS_NFC_KEYSTORE_PASSWORD` (secret) | Existing store password |
| `REWARDS_NFC_KEY_ALIAS` (secret) | Existing production alias |
| `REWARDS_NFC_KEY_PASSWORD` (secret) | Existing key password |
| `ANDROID_RELEASE_CERT_SHA256` (variable) | `44d1aef5aeb847cf4d4ff1f7387fecb971dc8c163681b26921cf70141c282b16` |

No production signing, tags, push, publication, or releases were performed by
this build-integrity patch. Workstation signer files remain untouched. The Nostr
key stays local, outside GitHub. Publication gets `contents: write` only in its
separate job; source review status reads require `statuses: read`.

Remaining recommended controls, **not claimed configured**: eligible independent
human PR/CODEOWNER approvals, required release-environment human reviewers and
no administrator bypass, immutable `v*` tag protection, allowed-Actions policy
and fork approval policy. The release gate authenticates the latest review
status creator against the repository variable `INDEPENDENT_REVIEW_PRODUCER_ID`
(immutable GitHub user ID `98620768`, verified for the authorized operator),
in addition to context, exact SHA and success. Missing configuration/creator or
a newer status from another producer fails closed. Protect the operator's OAuth
credential and repository-variable administration: a compromised trusted operator
can still forge its own status. This is an authenticated trusted-operator signal,
not cryptographic model attestation or a dedicated GitHub App check (`app_id: null`).
Organization-level secret availability is not established.

## Gradle artifact integrity and bootstrap trust

Gradle stays **8.4**, AGP stays **8.2.2**, and all four direct application/test
coordinates retain their exact versions (material 1.11.0, zxing 4.3.0, JUnit
4.13.2, org.json 20231013). No application source or dependency version upgrades
are included. The old launcher/JAR pair was not the official 8.4 wrapper;
`gradlew`, `gradlew.bat`, and the JAR are now the matching upstream `v8.4.0`
wrapper files. The official 8.4 JAR uses `GradleWrapperMain` with a classpath,
not the previous launcher's executable-JAR invocation.

Official HTTPS checksum sources fetched on 2026-11-07:

- `https://services.gradle.org/distributions/gradle-8.4-bin.zip.sha256`:
  `3e1af3ae886920c3ac87f7a91f816c0c7c436f276a6eefdb3da152100fef72ae`
- `https://services.gradle.org/distributions/gradle-8.4-wrapper.jar.sha256`:
  `0336f591bc0ec9aa0c9988929b93ecc916b3c1d52aed202c7381db144aa0ef15`

The JAR was fetched from
`https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar`
and matched the official published hash. Both launchers come from that same
upstream tag. `verify-gradle-wrapper.py` checks the exact JAR hash, expected
HTTPS distribution URL, checksum pin and URL-validation setting **before Java
executes**, in CI and in the signing script. The wrapper's own distribution
checksum validation protects new distribution installations; it does not rehash
an already-extracted local Gradle installation. CI uses fresh hosted runners,
without restored Gradle caches. Local Gradle/JDK/SDK installations still need a
trusted operator and filesystem.

`gradle/verification-metadata.xml` pins **477 SHA-256 entries for 477 artifacts
across 275 components**, including AGP/buildscript transitives, AndroidX,
debug/release test and runtime artifacts, lint and their resolved metadata.
`verify-metadata` is true; there are **no trusted-artifact wildcards, missing-hash
exceptions or ignored components**. Strict verification is explicit in both
clean CI and production-signing Gradle commands. New unrecognized versions or
artifact bytes fail instead of silently expanding trust. This uses strict
checksum metadata rather than dependency lockfiles; version constraints remain
unchanged. Platform-specific artifacts not in the reviewed metadata fail closed;
new OS/architecture support needs a deliberate metadata review.

Bootstrap provenance is **trust on first use**, not signed certification:
Gradle generated SHA-256 metadata from the actual artifacts resolved by the full
clean lint/test/debug-assembly gate, including its local dependency cache. An
independent fetch of **every one of the 477 recorded artifacts** from the
configured official Google Maven (`https://dl.google.com/dl/android/maven2/`)
or Maven Central (`https://repo.maven.apache.org/maven2/`) HTTPS repositories
then matched all 477 hashes. This confirms repository-served bytes at bootstrap;
it does not prove publisher identity, uncompromised upstream source, signatures,
or reproducible builds. Signature verification is explicitly false. Android SDK,
JDK, hosted runner images, and executable downloaded policy tools are separate
trust inputs, not covered by Gradle dependency metadata. Checksums do not make
reviewed malicious build scripts safe with signing secrets available.

`test-gradle-integrity.py` builds a fresh isolated local-Maven JAR fixture, checks
that Gradle accepts its pinned original, changes only that fixture, then requires
a nonzero **Dependency verification failed** result and a report identifying
its pinned artifact/hash. It never tampers with production dependencies. The
Python CI suite also accepts an original copied wrapper and rejects a changed
copy and removed distribution checksum. No real artifact is grandfathered.

For an intentional dependency change, review coordinates and upstream source,
then bootstrap in a credential-free environment with the same full clean task
list and `--write-verification-metadata sha256`. Independently check new hashes
against the configured repository downloads, inspect the XML diff, and rerun
without the write flag under `--dependency-verification strict`. Never use
`off`/`lenient`, broad trust entries, or auto-regeneration in CI to hide failures.

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
   - latest **Independent OpenCode review** status for that exact SHA is success
     (all response pages checked, SHA identity required; no absent-status bypass);
   - tag equals app versionName and Zapstore release_source names the exact
     expected GitHub repository/tag/production APK.
4. The operator confirms exact SHA, acceptance and certificate before dispatch;
   no environment human-review gate is currently configured. Signing verifies the
   official wrapper then reruns clean debug/release lint, both unit variants and
   `assembleRelease` under strict dependency verification, rejects
   missing secrets, verifies the APK signature with `apksigner`, and requires the
   independently established certificate hash. Private temporary keystore is
   removed on exit. Only the signed APK, SHA256 and source SHA leave the job;
   never the keystore/properties. Secrets are not available to policy/PR jobs.
5. The separate `release-publication` job (no required human approval currently)
   receives publication write access. It downloads the artifact from this same run, rechecks source/main/tag/CI,
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
main/tag/version/CI/external-review release gates, missing signing secrets and real Gitleaks
finding sanitization. Fixture CI responses are test doubles, **not** a claim
that GitHub Actions ran. Signed build/publication require configured credentials
and human authorization and are not exercised by these tests.
