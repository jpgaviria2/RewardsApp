from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[2]
manifest = root / 'app/src/debug/AndroidManifest.xml'
assert manifest.exists(), 'Offline diagnostic manifest is missing'
s = manifest.read_text()
for permission in ('INTERNET', 'NFC', 'CAMERA'):
    assert 'android.permission.' + permission + '" tools:node="remove"' in s
assert 'HidDiagnosticActivity' in s
assert '.MainActivity" tools:node="remove"' in s
activity = (root / 'app/src/debug/java/com/bitcoinrewards/nfcdisplay/HidDiagnosticActivity.java').read_text()
assert 'private final HidScannerInput input = HidScannerInput.processInput();' in activity
assert 'new HidScannerInput()' not in activity, 'diagnostic must share process framing authority'
assert 'input.handle(event, true, this::received)' in activity
assert 'WalletQrPayloadParser.parse(raw)' in activity
assert 'coordinator.begin' not in activity
assert 'HttpURLConnection' not in activity
assert 'applicationIdSuffix ".hidtest"' in (root / 'app/build.gradle').read_text()
print('PASS offline harness isolation and real input/parser wiring')
