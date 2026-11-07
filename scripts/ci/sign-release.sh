#!/usr/bin/env bash
set -euo pipefail
# Called ONLY in the protected release-signing environment after source gate.
: "${ANDROID_KEYSTORE_BASE64:?missing preserved keystore}"
: "${REWARDS_NFC_KEYSTORE_PASSWORD:?missing store password}"
: "${REWARDS_NFC_KEY_ALIAS:?missing existing key alias}"
: "${REWARDS_NFC_KEY_PASSWORD:?missing key password}"
: "${ANDROID_RELEASE_CERT_SHA256:?missing independently verified installed certificate hash}"
tag=${1:?existing reviewed tag}
[[ $tag =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]
[[ $ANDROID_RELEASE_CERT_SHA256 =~ ^[0-9a-fA-F]{64}$ ]]
python3 scripts/ci/verify-gradle-wrapper.py
keydir=$(mktemp -d)
trap 'rm -rf "$keydir"' EXIT
umask 077
export REWARDS_NFC_KEYSTORE="$keydir/preserved-release.jks"
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$REWARDS_NFC_KEYSTORE"
unset ANDROID_KEYSTORE_BASE64
./gradlew clean lintDebug lintRelease testDebugUnitTest testReleaseUnitTest assembleRelease --no-daemon --no-build-cache --rerun-tasks --dependency-verification strict
python3 scripts/ci/verify-tests.py
apk=app/build/outputs/apk/release/app-release.apk
"${ANDROID_HOME:?}/build-tools/34.0.0/aapt" dump badging "$apk" > "$keydir/badging.txt"
python3 - "$keydir/badging.txt" "${tag#v}" <<'PY'
import pathlib, re, sys
badging = pathlib.Path(sys.argv[1]).read_text()
source = pathlib.Path("app/build.gradle").read_text()
code = re.findall(r'\bversionCode\s+(\d+)', source)
package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
if len(code) != 1 or package is None or package.groups() != ("com.bitcoinrewards.nfcdisplay", code[0], sys.argv[2]):
    raise SystemExit("APK package/version metadata mismatch")
PY
signer="${ANDROID_HOME:?}/build-tools/34.0.0/apksigner"
"$signer" verify --verbose --print-certs "$apk" > "$keydir/cert.txt"
actual=$(python3 - "$keydir/cert.txt" <<'PY'
import pathlib, re, sys
certs = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)$', pathlib.Path(sys.argv[1]).read_text(), re.M)
if len(certs) != 1:
    raise SystemExit("Require exactly one APK signer")
print(certs[0].lower())
PY
)
[[ $actual == "${ANDROID_RELEASE_CERT_SHA256,,}" ]] || { echo 'Signing certificate mismatch'; exit 1; }
mkdir -p release-artifacts
name="rewards-nfc-display-${tag#v}-production.apk"
cp "$apk" "release-artifacts/$name"
(cd release-artifacts && sha256sum "$name" > "$name.sha256")
printf '%s\n' "${GITHUB_SHA:?}" > release-artifacts/SOURCE_COMMIT
