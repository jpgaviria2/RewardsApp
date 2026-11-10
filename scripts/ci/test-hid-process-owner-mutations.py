#!/usr/bin/env python3
"""Kill per-surface framing regressions using actual source-assembled replay.
Temporary source copies only; no Android, devices, credentials or networking.
"""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
main = Path('app/src/main/java/com/bitcoinrewards/nfcdisplay')
debug = Path('app/src/debug/java/com/bitcoinrewards/nfcdisplay')
paths = [main / (name + '.java') for name in (
    'MainActivity', 'SettingsActivity', 'LoginCodeScanActivity', 'NativePosActivity',
    'PaymentActivity', 'RewardsProfileScanActivity')]
paths += [debug / 'HidDiagnosticActivity.java']
before = {str(p): hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in paths}
for path in paths:
    original = (ROOT / path).read_text()
    mutant = original.replace('HidScannerInput.processInput()', 'new HidScannerInput()')
    assert mutant != original, path
    with tempfile.TemporaryDirectory(prefix='hid-owner-mutation-', dir=ROOT.parent) as temp:
        work = Path(temp)
        shutil.copytree(ROOT / 'app/src', work / 'app/src')
        (work / 'scripts/ci').mkdir(parents=True)
        shutil.copyfile(ROOT / 'scripts/ci/test-hid-replay.py', work / 'scripts/ci/test-hid-replay.py')
        (work / path).write_text(mutant)
        result = subprocess.run(['python3', str(work / 'scripts/ci/test-hid-replay.py')],
                                capture_output=True, text=True)
        expected = ('cross-Activity blocked prefix submitted wrong destination: [bob@example.com]'
                    if path.stem in {'MainActivity', 'SettingsActivity'} else
                    'Main/profile lifecycle suffix' if path.stem == 'RewardsProfileScanActivity' else
                    'debug diagnostic must share owner' if path.stem == 'HidDiagnosticActivity' else
                    path.stem + ' must share process owner')
        assert result.returncode != 0 and expected in result.stderr, (path, result.stdout, result.stderr)
        print('PASS mutation killed: ' + path.stem + ': ' + expected)
assert before == {str(p): hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in paths}
print('PASS all 7 real production initializers guarded; candidate unchanged')
