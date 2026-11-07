#!/usr/bin/env bash
set -euo pipefail
sha=${1:?reviewed sha}
tag=${2:?existing tag}
python3 scripts/ci/release-gate.py "$sha" "$tag"
name="rewards-nfc-display-${tag#v}-production.apk"
[[ $(cat release-artifacts/SOURCE_COMMIT) == "$sha" ]]
(cd release-artifacts && sha256sum --check "$name.sha256")
# No clobber, no existing-release repair, no tag creation.
if gh release view "$tag" >/dev/null 2>&1; then
  echo 'Release already exists; refusing replacement'
  exit 1
fi
gh release create "$tag" "release-artifacts/$name" "release-artifacts/$name.sha256" \
  --verify-tag --target "$sha" --title "Rewards NFC Display ${tag#v}" \
  --notes-file docs/ZAPSTORE-RELEASE-NOTES.md --latest
# Read back state and public bytes, not just the upload command exit code.
gh release view "$tag" --json isDraft,isPrerelease,tagName > release-artifacts/release-state.json
python3 - "$tag" <<'PY'
import json,sys
x=json.load(open("release-artifacts/release-state.json"))
assert not x['isDraft'] and not x['isPrerelease'] and x['tagName'] == sys.argv[1]
PY
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl --fail --location --silent --show-error --retry 4 \
  "https://github.com/$GITHUB_REPOSITORY/releases/download/$tag/$name" -o "$tmp/$name"
cmp "release-artifacts/$name" "$tmp/$name"
gh api "repos/$GITHUB_REPOSITORY/releases/latest" --jq .tag_name > "$tmp/latest"
[[ $(cat "$tmp/latest") == "$tag" ]]
echo 'GitHub release and public APK verified; Zapstore promotion remains separate'
