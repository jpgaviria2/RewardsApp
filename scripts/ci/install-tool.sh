#!/usr/bin/env bash
set -euo pipefail
# Official immutable release assets; fail closed on checksum or platform mismatch.
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]]
dest=${2:?usage: install-tool.sh gitleaks-or-actionlint destination}
case ${1:?tool required} in
  gitleaks) repo=gitleaks/gitleaks; version=8.30.1; asset=gitleaks_8.30.1_linux_x64.tar.gz; sha=551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb ;;
  actionlint) repo=rhysd/actionlint; version=1.7.12; asset=actionlint_1.7.12_linux_amd64.tar.gz; sha=8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8 ;;
  *) exit 2 ;;
esac
mkdir -p "$dest"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl --fail --silent --show-error --location "https://github.com/$repo/releases/download/v$version/$asset" -o "$tmp/tool.tar.gz"
printf '%s  %s\n' "$sha" "$tmp/tool.tar.gz" | sha256sum --check --status
tar -xzf "$tmp/tool.tar.gz" -C "$tmp" "$1"
install -m 0755 "$tmp/$1" "$dest/$1"
"$dest/$1" --version
