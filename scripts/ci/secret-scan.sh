#!/usr/bin/env bash
set -euo pipefail
# Scan tracked reachable history, never local ignored .secrets/ or build outputs.
# Never print raw scanner output or upload its raw report, even with --redact.
out=${1:-ci-reports}
mkdir -p "$out"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
printf '[extend]\nuseDefault = true\n' > "$tmp/config.toml"
set +e
gitleaks git --config "$tmp/config.toml" --gitleaks-ignore-path scripts/ci/gitleaks-history.ignore --redact=100 --no-banner --log-opts="--all" --report-format json --report-path "$tmp/findings.json" > "$tmp/log" 2>&1
status=$?
set -e
python3 - "$tmp/findings.json" "$out/secret-findings.json" "$status" <<'PY'
import json, pathlib, sys
source, target, status = sys.argv[1:]
if int(status) not in (0, 1):
    raise SystemExit("Secret scanner failed; raw logs withheld to prevent disclosure")
if not pathlib.Path(source).is_file():
    raise SystemExit("Secret scanner produced no report")
raw = json.loads(pathlib.Path(source).read_text()) or []
# Deliberately exclude Secret, Match, commit messages and all arbitrary text.
findings = [{k: item.get(k) for k in ("RuleID", "File", "StartLine", "EndLine", "Commit")} for item in raw]
pathlib.Path(target).write_text(json.dumps(findings, indent=2) + "\n")
print(f"Secret scan: {len(findings)} findings; sanitized locations saved to {target}")
if findings:
    raise SystemExit(1)
PY
exit "$status"
