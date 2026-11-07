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
    def test_android_gate_includes_both_lints_and_unit_variants_without_release_signing(self):
        text = (SCRIPTS / "android-check.sh").read_text()
        for task in ("clean", "lintDebug", "lintRelease", "testDebugUnitTest", "testReleaseUnitTest", "assembleDebug"):
            self.assertIn(task, text)
        self.assertNotIn("assembleRelease", text)
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
    def tearDown(self):
        self.temp.cleanup()
    def wrapper_fixture(self):
        folder = self.root / "gradle/wrapper"
        folder.mkdir(parents=True)
        for name in ("gradle-wrapper.jar", "gradle-wrapper.properties"):
            shutil.copyfile(SCRIPTS.parent.parent / "gradle/wrapper" / name, folder / name)
        return folder
    def test_wrapper_original_and_tampered_copy(self):
        folder = self.wrapper_fixture()
        self.assertEqual(run("verify-gradle-wrapper.py", self.root).returncode, 0)
        with (folder / "gradle-wrapper.jar").open("ab") as artifact:
            artifact.write(b"fixture tamper")
        p = run("verify-gradle-wrapper.py", self.root)
        self.assertNotEqual(p.returncode, 0)
        self.assertIn("checksum mismatch", p.stderr)
    def test_wrapper_distribution_pin_cannot_be_removed(self):
        folder = self.wrapper_fixture()
        path = folder / "gradle-wrapper.properties"
        path.write_text("\n".join(line for line in path.read_text().splitlines() if not line.startswith("distributionSha256Sum=")))
        self.assertNotEqual(run("verify-gradle-wrapper.py", self.root).returncode, 0)
    def test_gradle_gates_require_strict_verification_before_execution(self):
        for name in ("android-check.sh", "sign-release.sh"):
            text = (SCRIPTS / name).read_text()
            self.assertIn("--dependency-verification strict", text)
            self.assertLess(text.index("verify-gradle-wrapper.py"), text.index("./gradlew"))
    def test_scope_accepts_only_exact_integrity_paths(self):
        base = self.init_git()
        for name in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.properties", "gradle/wrapper/gradle-wrapper.jar", "gradle/verification-metadata.xml"):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("fixture")
        self.git("add", ".")
        self.git("commit", "-qm", "integrity fixture")
        self.assertEqual(run("feature-scope.py", base, "HEAD", cwd=self.root).returncode, 0)
        (self.root / "gradle/unrelated.gradle").write_text("unrelated")
        self.git("add", ".")
        self.git("commit", "-qm", "unrelated gradle fixture")
        self.assertNotEqual(run("feature-scope.py", base, "HEAD", cwd=self.root).returncode, 0)
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
    def test_scope_accepts_exact_checkin_transfer_files_not_unrelated_scanner_changes(self):
        base = self.init_git()
        for name in ("app/src/main/java/com/bitcoinrewards/nfcdisplay/CollectionCheckInStore.java",
                     "app/src/test/java/com/bitcoinrewards/nfcdisplay/CollectionCheckInStoreTest.java",
                     "app/src/test/java/com/bitcoinrewards/nfcdisplay/ScannerEntrySourceTest.java"):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("fixture")
        self.git("add", ".")
        self.git("commit", "-qm", "transfer fixtures")
        self.assertEqual(run("feature-scope.py", base, "HEAD", cwd=self.root).returncode, 0)
        (self.root / "app/src/main/java/com/bitcoinrewards/nfcdisplay/RewardsProfileScanActivity.java").write_text("unrelated")
        self.git("add", ".")
        self.git("commit", "-qm", "unrelated scanner")
        self.assertNotEqual(run("feature-scope.py", base, "HEAD", cwd=self.root).returncode, 0)
    def release_fixture(self, conclusion="success", version="1.2.3", review="success", review_sha=None, newer_review=None,
                        review_creator: int | None =98620768, newer_creator=98620768):
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
        statuses = [] if review is None else [{"context": "Independent OpenCode review", "state": review, "id": 1,
                                              "creator": {"id": review_creator},
                                              "url": f"https://api.github.com/repos/fixture/fixture/statuses/{review_sha or sha}"}]
        if newer_review is not None:
            statuses.append({"context": "Independent OpenCode review", "state": newer_review, "id": 2,
                             "creator": {"id": newer_creator},
                             "url": f"https://api.github.com/repos/fixture/fixture/statuses/{review_sha or sha}"})
        pages = [statuses]
        gh.write_text("#!/usr/bin/env python3\nimport sys\n"
                      "if '--slurp' in sys.argv:\n"
                      "    assert any('/commits/" + sha + "/statuses?' in arg for arg in sys.argv), 'Use status-list endpoint with creator identity'\n"
                      "    print(" + repr(json.dumps(pages)) + ")\n"
                      "else:\n    print(" + repr(json.dumps(runs)) + ")\n")
        gh.chmod(0o755)
        env = dict(os.environ, GITHUB_REF="refs/heads/main", GITHUB_REPOSITORY="fixture/fixture",
                   INDEPENDENT_REVIEW_PRODUCER_ID="98620768", PATH=str(tools) + os.pathsep + os.environ["PATH"])
        return sha, env
    def test_release_valid_fixture(self):
        sha, env = self.release_fixture()
        p = run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env)
        self.assertEqual(p.returncode, 0, p.stderr)
    def test_release_missing_independent_review(self):
        sha, env = self.release_fixture(review=None)
        p = run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env)
        self.assertNotEqual(p.returncode, 0)
        self.assertIn("Independent OpenCode review must succeed", p.stderr)
    def test_release_unauthorized_review_producer(self):
        sha, env = self.release_fixture(review_creator=123)
        p = run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env)
        self.assertNotEqual(p.returncode, 0)
        self.assertIn("unauthorized producer", p.stderr)
    def test_release_review_missing_creator(self):
        sha, env = self.release_fixture(review_creator=None)
        self.assertNotEqual(run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env).returncode, 0)
    def test_release_newer_forged_success_rejects_even_after_trusted_success(self):
        sha, env = self.release_fixture(newer_review="success", newer_creator=123)
        self.assertNotEqual(run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env).returncode, 0)
    def test_release_review_producer_configuration_required(self):
        sha, env = self.release_fixture()
        env.pop("INDEPENDENT_REVIEW_PRODUCER_ID")
        self.assertNotEqual(run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env).returncode, 0)
    def test_release_failed_or_pending_independent_review(self):
        for state in ("failure", "pending", "error"):
            with self.subTest(state=state):
                # Each nested case needs its own independent repository fixture.
                with tempfile.TemporaryDirectory() as temp:
                    original = self.root
                    self.root = pathlib.Path(temp)
                    try:
                        sha, env = self.release_fixture(review=state)
                        p = run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env)
                        self.assertNotEqual(p.returncode, 0)
                        self.assertIn("Independent OpenCode review must succeed", p.stderr)
                    finally:
                        self.root = original
    def test_release_review_wrong_sha(self):
        sha, env = self.release_fixture(review_sha="0" * 40)
        self.assertNotEqual(run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env).returncode, 0)
    def test_release_newer_review_failure_overrides_old_success(self):
        sha, env = self.release_fixture(newer_review="failure")
        self.assertNotEqual(run("release-gate.py", sha, "v1.2.3", cwd=self.root, env=env).returncode, 0)
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
