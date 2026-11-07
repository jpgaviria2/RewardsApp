#!/usr/bin/env python3
"""Read-only exact source/tag/CI gate, run BEFORE any signing secret is loaded."""
import json
import os
import re
import subprocess
import sys

def out(*args):
    return subprocess.check_output(args, text=True).strip()

sha, tag = sys.argv[1:]
if not re.fullmatch(r"[0-9a-f]{40}", sha) or not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
    raise SystemExit("Require full lowercase reviewed SHA and vX.Y.Z tag")
if os.environ.get("GITHUB_REF") != "refs/heads/main":
    raise SystemExit("Dispatch workflow only from main")
subprocess.run(["git", "fetch", "origin", "main", f"refs/tags/{tag}:refs/tags/{tag}"], check=True)
if out("git", "rev-parse", "origin/main") != sha or out("git", "rev-parse", "HEAD") != sha:
    raise SystemExit("Reviewed SHA must equal current main and checked out source")
subprocess.run(["git", "merge-base", "--is-ancestor", sha, "origin/main"], check=True)
if out("git", "rev-parse", tag + "^{commit}") != sha:
    raise SystemExit("Existing immutable tag does not resolve to reviewed main SHA")
repo = os.environ["GITHUB_REPOSITORY"]
runs = json.loads(out("gh", "api", f"repos/{repo}/actions/workflows/android-ci.yml/runs?head_sha={sha}&event=push&per_page=100"))["workflow_runs"]
runs = [r for r in runs if r["head_sha"] == sha and r["head_branch"] == "main" and r["event"] == "push"]
if not runs or max(runs, key=lambda r: r["id"])["conclusion"] != "success":
    raise SystemExit("Latest main push Android CI for exact reviewed SHA must succeed")
review_pages = json.loads(out("gh", "api", "--paginate", "--slurp",
                              f"repos/{repo}/commits/{sha}/status?per_page=100"))
if not review_pages or any(page.get("sha") != sha for page in review_pages):
    raise SystemExit("Independent review status response must identify exact reviewed SHA")
reviews = [status for page in review_pages for status in page.get("statuses", [])
           if status.get("context") == "Independent OpenCode review"]
if not reviews or max(reviews, key=lambda status: status["id"])["state"] != "success":
    raise SystemExit("Independent OpenCode review must succeed on exact reviewed SHA")
producer = os.environ.get("INDEPENDENT_REVIEW_PRODUCER_ID", "")
if not re.fullmatch(r"[1-9][0-9]*", producer):
    raise SystemExit("Require configured immutable independent review producer ID")
latest_review = max(reviews, key=lambda status: status["id"])
creator_id = (latest_review.get("creator") or {}).get("id")
if type(creator_id) is not int or creator_id != int(producer):
    raise SystemExit("Independent OpenCode review has an unauthorized producer")
source = out("git", "show", f"{sha}:app/build.gradle")
versions = re.findall(r'versionName\s+"([^"\n]+)"', source)
if versions != [tag[1:]]:
    raise SystemExit("Tag/versionName mismatch")
expected = f"https://github.com/{repo}/releases/download/{tag}/rewards-nfc-display-{tag[1:]}-production.apk"
manifest = out("git", "show", f"{sha}:zapstore.yaml")
if f"release_source: {expected}" not in manifest.splitlines():
    raise SystemExit("Zapstore canonical release source mismatch")
print(f"Release source accepted: {sha} {tag}; exact main CI and Independent OpenCode review successful")
