#!/usr/bin/env python3
"""Real Gradle strict verification against an isolated local-Maven tamper fixture."""
import hashlib
import pathlib
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]

def main():
    subprocess.run(["python3", str(ROOT / "scripts/ci/verify-gradle-wrapper.py")], check=True)
    with tempfile.TemporaryDirectory(prefix="gradle-integrity-") as temp:
        root = pathlib.Path(temp)
        jar = root / "repo/ci/integrity/probe/1.0/probe-1.0.jar"
        jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("probe.txt", "original fixture bytes\n")
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        (root / "settings.gradle").write_text("rootProject.name = 'integrity-negative-fixture'\n")
        (root / "build.gradle").write_text('''repositories { maven { url = uri('repo'); metadataSources { artifact() } } }
configurations { probe }
dependencies { probe 'ci.integrity:probe:1.0' }
tasks.register('resolveProbe') { doLast { configurations.probe.files.each { println it.name } } }
''')
        (root / "gradle").mkdir()
        (root / "gradle/verification-metadata.xml").write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">
 <configuration><verify-metadata>true</verify-metadata><verify-signatures>false</verify-signatures></configuration>
 <components><component group="ci.integrity" name="probe" version="1.0">
  <artifact name="probe-1.0.jar"><sha256 value="{digest}"/></artifact>
 </component></components>
</verification-metadata>
''')
        command = [str(ROOT / "gradlew"), "-p", str(root), "resolveProbe", "--offline",
                   "--no-daemon", "--dependency-verification", "strict", "--console=plain"]
        good = subprocess.run(command, capture_output=True, text=True)
        if good.returncode:
            raise SystemExit("Untampered dependency fixture failed:\n" + good.stdout + good.stderr)
        with zipfile.ZipFile(jar, "a") as archive:
            archive.writestr("tampered.txt", "changed fixture bytes\n")
        bad = subprocess.run(command, capture_output=True, text=True)
        output = bad.stdout + bad.stderr
        if bad.returncode == 0 or "Dependency verification failed" not in output:
            raise SystemExit("Expected strict checksum failure was not observed:\n" + output)
        reports = list(root.glob("build/reports/dependency-verification/**/dependency-verification-report.html"))
        detail = "\n".join(p.read_text() for p in reports)
        changed_digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        if digest not in detail or changed_digest not in detail or "probe-1.0.jar" not in detail:
            raise SystemExit("Failure report did not identify the pinned fixture artifact/checksum:\n" + output)
        print("Strict dependency fixture: original PASS; tampered JAR REJECTED (nonzero checksum failure)")

if __name__ == "__main__":
    main()
