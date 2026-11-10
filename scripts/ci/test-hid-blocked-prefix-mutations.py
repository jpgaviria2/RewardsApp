#!/usr/bin/env python3
"""Offline mutation controls; modify temporary copies, never the candidate."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
ADAPTER = Path('app/src/main/java/com/bitcoinrewards/nfcdisplay/HidScannerInput.java')
source = (ROOT / ADAPTER).read_text()
start = source.index('            if (delimiter && event.getAction() == KeyEvent.ACTION_UP) {')
end = source.index('            return true;', start)
mutations = {
    'reset-only loses blocked prefix': (
        source[:start] + '            framer.reset();\n' + source[end:],
        'disabled prefix must never submit valid suffix bob@example.com'),
    'blocked delimiter never clears discard': (
        source.replace('delimiter && event.getAction() == KeyEvent.ACTION_DOWN,', 'false,'),
        'consume-only Enter clears discard including UP'),
}
for name, (mutant, expected) in mutations.items():
    assert mutant != source, name + ' did not mutate source'
    with tempfile.TemporaryDirectory(prefix='hid-mutation-', dir=ROOT.parent) as temp:
        work = Path(temp)
        shutil.copytree(ROOT / 'app/src/main/java', work / 'app/src/main/java')
        shutil.copytree(ROOT / 'app/src/debug/java', work / 'app/src/debug/java')
        (work / 'app/src/main/AndroidManifest.xml').write_bytes(
            (ROOT / 'app/src/main/AndroidManifest.xml').read_bytes())
        (work / 'scripts/ci').mkdir(parents=True)
        shutil.copyfile(ROOT / 'scripts/ci/test-hid-replay.py', work / 'scripts/ci/test-hid-replay.py')
        (work / ADAPTER).write_text(mutant)
        result = subprocess.run(['python3', str(work / 'scripts/ci/test-hid-replay.py')],
                                capture_output=True, text=True)
        assert result.returncode != 0 and expected in result.stderr, (name, result.stdout, result.stderr)
        print('PASS mutation killed: ' + name + ': ' + expected)
assert (ROOT / ADAPTER).read_text() == source, 'candidate changed during mutations'
print('PASS candidate unchanged; mutations used temporary copies only')
