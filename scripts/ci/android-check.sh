#!/usr/bin/env bash
set -euo pipefail
# No signing material, caches, or release assembly in untrusted CI.
./gradlew clean lintDebug lintRelease testDebugUnitTest testReleaseUnitTest assembleDebug --no-daemon --no-build-cache --rerun-tasks
python3 scripts/ci/verify-tests.py
git diff --exit-code
