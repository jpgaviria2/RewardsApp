#!/usr/bin/env python3
"""Verify the wrapper before executing Java. No network or Gradle required."""
import hashlib
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
# Official https://services.gradle.org/distributions/gradle-8.4-wrapper.jar.sha256
WRAPPER_SHA256 = "0336f591bc0ec9aa0c9988929b93ecc916b3c1d52aed202c7381db144aa0ef15"
# Official https://services.gradle.org/distributions/gradle-8.4-bin.zip.sha256
DISTRIBUTION_SHA256 = "3e1af3ae886920c3ac87f7a91f816c0c7c436f276a6eefdb3da152100fef72ae"

def verify(root):
    wrapper = root / "gradle/wrapper"
    actual = hashlib.sha256((wrapper / "gradle-wrapper.jar").read_bytes()).hexdigest()
    if actual != WRAPPER_SHA256:
        raise ValueError("Gradle wrapper JAR checksum mismatch")
    properties = {}
    for line in (wrapper / "gradle-wrapper.properties").read_text().splitlines():
        if not line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        if key in properties:
            raise ValueError("Duplicate wrapper property: " + key)
        properties[key] = value
    if properties.get("distributionUrl") != r"https\://services.gradle.org/distributions/gradle-8.4-bin.zip":
        raise ValueError("Unexpected Gradle distribution URL")
    if properties.get("distributionSha256Sum") != DISTRIBUTION_SHA256:
        raise ValueError("Gradle distribution checksum pin mismatch")
    if properties.get("validateDistributionUrl") != "true":
        raise ValueError("Gradle distribution URL validation must stay enabled")
    print("Gradle 8.4 wrapper JAR and distribution checksum pins verified")

if __name__ == "__main__":
    try:
        verify(pathlib.Path(sys.argv[1]) if len(sys.argv) == 2 else ROOT)
    except (ValueError, OSError) as error:
        raise SystemExit(str(error))
