#!/usr/bin/env python3
"""Offline regression tests; isolated scratch fixtures, never production credentials."""
import json
import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

SCRIPTS = pathlib.Path(__file__).resolve().parent

def run(script, *args, cwd=None, env=None):
    command = ["bash" if script.endswith(".sh") else "python3", str(SCRIPTS / script), *map(str, args)]
    return subprocess.run(command, cwd=cwd, env=env, text=True, capture_output=True)

class Gates(unittest.TestCase):
    def test_android_setup_avoids_obsolete_tools_package(self):
        workflows = SCRIPTS.parent.parent / ".github/workflows"
        for name in ("android-ci.yml", "reviewed-release.yml"):
            text = (workflows / name).read_text()
            setup = text.split("uses: android-actions/setup-android@", 1)[1].split("      - ", 1)[0]
            self.assertIn("packages: platform-tools", setup, name)
            self.assertNotRegex(setup, r"packages:.*\btools\s+platform-tools", name)
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
    def tearDown(self):
        self.temp.cleanup()
    def reports(self, **counts):
        for variant in ("testDebugUnitTest", "testReleaseUnitTest"):
            folder = self.root / variant
            folder.mkdir(exist_ok=True)
            attrs = {"tests": 2, "skipped": 0, "failures": 0, "errors": 0, **counts}
            (folder / "TEST-fixture.xml").write_text('<testsuite ' + ' '.join(f'{k}="{v}"' for k,v in attrs.items()) + '/>')
    def test_passing_reports(self):
        self.reports()
        self.assertEqual(run("verify-tests.py", self.root).returncode, 0)
    def test_absent_reports(self):
        self.assertNotEqual(run("verify-tests.py", self.root).returncode, 0)
    def test_zero_executed(self):
        self.reports(tests=0)
        self.assertNotEqual(run("verify-tests.py", self.root).returncode, 0)
    def test_all_skipped(self):
        self.reports(skipped=2)
        self.assertNotEqual(run("verify-tests.py", self.root).returncode, 0)
    def test_failed_tests(self):
        self.reports(failures=1)
        self.assertNotEqual(run("verify-tests.py", self.root).returncode, 0)
    def test_error_tests(self):
        self.reports(errors=1)
        self.assertNotEqual(run("verify-tests.py", self.root).returncode, 0)
    def test_bad_xml(self):
        self.reports()
        (self.root / "testReleaseUnitTest/TEST-fixture.xml").write_text('broken')
        self.assertNotEqual(run("verify-tests.py", self.root).returncode, 0)
    def test_missing_release_reports(self):
        self.reports()
        shutil.rmtree(self.root / "testReleaseUnitTest")
        self.assertNotEqual(run("verify-tests.py", self.root).returncode, 0)
    def test_release_invalid_sha(self):
        self.assertNotEqual(run("release-gate.py", "main", "v1.2.3").returncode, 0)
    def test_release_invalid_tag(self):
        self.assertNotEqual(run("release-gate.py", "a" * 40, "--dangerous").returncode, 0)
    def test_release_nonmain_dispatch(self):
        env = dict(os.environ, GITHUB_REF="refs/heads/feature/merchant-collection")
        p = run("release-gate.py", "a" * 40, "v1.2.3", env=env)
        self.assertNotEqual(p.returncode, 0)
        self.assertIn("Dispatch workflow only from main", p.stderr)
    def test_signing_missing_secret(self):
        env = {k:v for k,v in os.environ.items() if not k.startswith(("ANDROID_KEYSTORE", "REWARDS_NFC_KEY"))}
        self.assertNotEqual(run("sign-release.sh", "v1.2.3", env=env).returncode, 0)
    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()
    def init_git(self):
        self.git("init", "-q")
        self.git("config", "user.name", "CI Fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("commit", "-qm", "fixture base", "--allow-empty")
        return self.git("rev-parse", "HEAD")
    def test_scope_rejects_unrelated_path(self):
        base = self.init_git()
        (self.root / "unrelated.txt").write_text("fixture")
        self.git("add", "unrelated.txt")
        self.git("commit", "-qm", "fixture change")
        p = run("feature-scope.py", base, "HEAD", cwd=self.root)
        self.assertNotEqual(p.returncode, 0)
        self.assertIn("OUT OF SCOPE", p.stdout)
    def test_scope_accepts_payment_path(self):
        base = self.init_git()
        path = self.root / "app/src/main/java/com/bitcoinrewards/nfcdisplay/PaymentApi.java"
        path.parent.mkdir(parents=True)
        path.write_text("fixture")
        self.git("add", ".")
        self.git("commit", "-qm", "fixture change")
        self.assertEqual(run("feature-scope.py", base, "HEAD", cwd=self.root).returncode, 0)
    def release_fixture(self, conclusion="success", version="1.2.3"):
        self.init_git()
        self.git("branch", "-M", "main")
        app = self.root / "app"
        app.mkdir()
        (app / "build.gradle").write_text('versionName "' + version + '"')
        (self.root / "zapstore.yaml").write_text("release_source: https://github.com/fixture/fixture/releases/download/v1.2.3/rewards-nfc-display-1.2.3-production.apk\n")
        self.git("add", ".")
        self.git("commit", "-qm", "release fixture")
        sha = self.git("rev-parse", "HEAD")
        self.git("tag", "v1.2.3")
        self.git("remote", "add", "origin", ".")
        tools = self.root / "fixture-bin"
        tools.mkdir()
        runs = {"workflow_runs": [{"head_sha":sha, "head_branch":"main", "event":"push", "conclusion":conclusion, "id":2}]}
        gh = tools / "gh"
        gh.write_text("#!/usr/bin/env python3\nprint(" + repr(json.dumps(runs)) + ")\n")
        gh.chmod(0o755)
        env = dict(os.environ, GITHUB_REF="refs/heads/main", GITHUB_REPOSITORY="fixture/fixture", PATH=str(tools) + os.pathsep + os.environ["PATH"])
        return sha, env
    def test_release_valid_fixture(self):
        sha, env = self.release_fixture()
        p = run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env)
        self.assertEqual(p.returncode, 0, p.stderr)
    def test_release_failed_main_ci(self):
        sha, env = self.release_fixture(conclusion="failure")
        self.assertNotEqual(run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env).returncode, 0)
    def test_release_version_mismatch(self):
        sha, env = self.release_fixture(version="9.9.9")
        self.assertNotEqual(run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env).returncode, 0)
    def test_release_tag_mismatch(self):
        sha, env = self.release_fixture()
        self.git("tag", "-f", "v1.2.3", "HEAD~1")
        self.assertNotEqual(run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env).returncode, 0)
    def test_release_not_current_main(self):
        sha, env = self.release_fixture()
        self.git("commit", "-qm", "new main fixture", "--allow-empty")
        self.assertNotEqual(run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env).returncode, 0)
    @unittest.skipUnless(shutil.which("gitleaks"), "install pinned gitleaks first")
    def test_secret_scan_reports_locations_not_values(self):
        self.init_git()
        folder = self.root / "scripts/ci"
        folder.mkdir(parents=True)
        (folder / "gitleaks-history.ignore").write_text("")
        # Deliberately synthetic fixture only; never usable as a credential.
        import secrets
        import string
        value = "ghp_" + "".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(36))
        (self.root / "fixture.txt").write_text('api_key = "' + value + '"')
        self.git("add", ".")
        self.git("commit", "-qm", "fixture only")
        p = run("secret-scan.sh", "reports", cwd=self.root)
        self.assertNotEqual(p.returncode, 0)
        report = (self.root / "reports/secret-findings.json").read_text()
        self.assertTrue(json.loads(report))
        self.assertNotIn(value, report + p.stdout + p.stderr)
        self.assertEqual(set(json.loads(report)[0]), {"RuleID", "File", "StartLine", "EndLine", "Commit"})

if __name__ == "__main__":
    unittest.main(verbosity=2)
