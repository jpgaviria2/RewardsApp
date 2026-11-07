#!/usr/bin/env python3
"""Fail closed if either Android unit-test variant did not execute passing tests."""
import pathlib
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results")
for variant in ("testDebugUnitTest", "testReleaseUnitTest"):
    reports = sorted((root / variant).glob("TEST-*.xml"))
    executed = failures = errors = 0
    for path in reports:
        suite = ET.parse(path).getroot()
        tests, skipped = int(suite.get("tests", 0)), int(suite.get("skipped", 0))
        if not 0 <= skipped <= tests:
            raise SystemExit(f"Invalid test counts: {path.name}")
        executed += tests - skipped
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
    print(f"{variant}: reports={len(reports)} executed={executed} failures={failures} errors={errors}")
    if not reports or executed <= 0 or failures or errors:
        raise SystemExit(f"FAIL: {variant} requires executed, passing tests")
