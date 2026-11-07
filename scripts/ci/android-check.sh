#!/usr/bin/env bash
set -euo pipefail
# No signing material, caches, or release assembly in untrusted CI.
python3 scripts/ci/verify-gradle-wrapper.py
python3 scripts/ci/test-gradle-integrity.py
./gradlew clean lintDebug lintRelease testDebugUnitTest testReleaseUnitTest assembleDebug --no-daemon --no-build-cache --rerun-tasks --dependency-verification strict
python3 scripts/ci/verify-tests.py
git diff --exit-code
