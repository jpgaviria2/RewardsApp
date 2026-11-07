#!/usr/bin/env python3
"""Temporary, fail-closed merchant-collection allowlist. Not a security sandbox."""
import subprocess
import sys

allowed = {
    "app/build.gradle", "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java",
    "app/src/main/res/layout/activity_main.xml",
    "docs/MERCHANT-COLLECTION-ACCEPTANCE.md", "docs/ZAPSTORE-RELEASE-NOTES.md",
    "zapstore.yaml",
}
for name in ("PaymentActivity", "PaymentApi", "PaymentContract", "PaymentOperationStore", "PaymentTapGate"):
    allowed.add(f"app/src/main/java/com/bitcoinrewards/nfcdisplay/{name}.java")
for name in ("PaymentApi", "PaymentContract", "PaymentModeSource", "PaymentOperationStore", "PaymentTapGate"):
    allowed.add(f"app/src/test/java/com/bitcoinrewards/nfcdisplay/{name}Test.java")
base, head = sys.argv[1:]
for ref in (base, head):
    subprocess.run(["git", "rev-parse", "--verify", ref + "^{commit}"], check=True, stdout=subprocess.DEVNULL)
paths = subprocess.check_output(["git", "diff", "--no-renames", "--name-only", "-z", base + "..." + head]).decode().split("\0")
blocked = [p for p in paths if p and p not in allowed and not p.startswith((".github/", "scripts/ci/")) and p != "docs/CI-CD.md"]
for path in blocked:
    print(f"OUT OF SCOPE: {path}")
if blocked:
    raise SystemExit(1)
print(f"Merchant collection scope: {sum(bool(p) for p in paths)} paths accepted")
