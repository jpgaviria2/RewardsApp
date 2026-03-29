# Task: v2.4.0 — Logo fix, remove NFC banner, show QR, add iOS app section

Update injectBrandingOverrides() in MainActivity.java with these changes:

## 1. Fix logo — wrap in cream pill background

Replace the current logo injection with one that wraps the logo in a cream rounded container so it's visible against the brown background:

```javascript
var container = document.querySelector('.container');
if (container && !document.getElementById('trails-logo-wrap')) {
    var wrap = document.createElement('div');
    wrap.id = 'trails-logo-wrap';
    wrap.style.cssText = 'background:#FFFEF7;border-radius:20px;padding:16px 24px;margin-bottom:20px;display:inline-block;box-shadow:0 4px 15px rgba(0,0,0,0.2);';
    var logo = document.createElement('img');
    logo.src = 'https://trailscoffee.com/LOGO-BROWN.png';
    logo.alt = 'Trails Coffee';
    logo.style.cssText = 'width:160px;max-width:55%;display:block;';
    wrap.appendChild(logo);
    container.insertBefore(wrap, container.firstChild);
}
```

## 2. Remove the NFC tap banner ("TAP YOUR PHONE HERE TO CLAIM")

The injectNfcBanner() method injects a div with id="nfc-tap-banner". Also hide the NFC tap button section:

```javascript
// Remove NFC banner
var nfcBanner = document.getElementById('nfc-tap-banner');
if (nfcBanner) nfcBanner.remove();

// Hide the NFC tap button section (#nfc-section)
var nfcSection = document.getElementById('nfc-section');
if (nfcSection) nfcSection.style.display = 'none';

// Also hide the NFC HCE indicator
var nfcIndicator = document.getElementById('nfc-hce-indicator');
if (nfcIndicator) nfcIndicator.style.display = 'none';
```

Also in injectNfcBanner() method — add a guard at the top so if trails-injected marker exists, don't inject the banner. OR just always remove nfc-tap-banner in a MutationObserver since injectNfcBanner runs after injectBrandingOverrides.

Best approach: add a MutationObserver to remove nfc-tap-banner whenever it appears:
```javascript
var observer = new MutationObserver(function() {
    var b = document.getElementById('nfc-tap-banner');
    if (b) b.remove();
    var s = document.getElementById('nfc-section');
    if (s) s.style.display = 'none';
});
observer.observe(document.body, {childList: true, subtree: true});
```

## 3. Add iOS App download section below the QR code

After the QR code div and before the instructions, inject an iOS download section:

```javascript
var qrDiv = document.querySelector('.qr-code');
if (qrDiv && !document.getElementById('ios-download-section')) {
    var iosSection = document.createElement('div');
    iosSection.id = 'ios-download-section';
    iosSection.style.cssText = 'background:linear-gradient(135deg,#6B4423,#8B4513);color:white;padding:16px 20px;border-radius:12px;margin:16px 0;text-align:center;';
    iosSection.innerHTML = '<div style="font-size:15px;font-weight:700;margin-bottom:6px;">📱 Claim on the Trails Coffee App</div>' +
        '<div style="font-size:13px;opacity:0.9;margin-bottom:12px;">Download from the App Store to collect & redeem your sats</div>' +
        '<a href="https://apps.apple.com/app/id6741817829" style="background:white;color:#6B4423;padding:10px 20px;border-radius:20px;font-size:13px;font-weight:700;text-decoration:none;display:inline-block;">⬇️ Download on App Store</a>';
    qrDiv.parentNode.insertBefore(iosSection, qrDiv.nextSibling);
}
```

Note: use the actual App Store link from MEMORY — bundle ID is me.anmore.trails-coffee. If the App Store link isn't known, use https://apps.apple.com/search?term=trails+coffee as fallback.

Also inject a similar iOS section on the waiting screen below the waiting message:
```javascript
var waitingDisplay = document.querySelector('.reward-display.waiting');
if (waitingDisplay && !document.getElementById('ios-waiting-section')) {
    var iosWait = document.createElement('div');
    iosWait.id = 'ios-waiting-section';
    iosWait.style.cssText = 'background:linear-gradient(135deg,#6B4423,#8B4513);color:white;padding:14px 18px;border-radius:12px;margin-top:20px;';
    iosWait.innerHTML = '<div style="font-size:14px;font-weight:700;margin-bottom:4px;">📱 Trails Coffee App</div>' +
        '<div style="font-size:12px;opacity:0.9;margin-bottom:10px;">Collect & redeem your Bitcoin rewards</div>' +
        '<a href="https://apps.apple.com/app/id6741817829" style="background:white;color:#6B4423;padding:8px 18px;border-radius:20px;font-size:12px;font-weight:700;text-decoration:none;display:inline-block;">Download on App Store</a>';
    waitingDisplay.appendChild(iosWait);
}
```

## 4. Keep all existing fixes from v2.3.0
- Brown CSS overrides
- Text replacements (Bitcoin → Coffee)
- Header text
- Back to Settings hidden
- Page title

## 5. Bump version
app/build.gradle: versionCode 14, versionName "2.4.0"

## 6. Commit and push
```bash
git add -A
git commit -m "feat: logo on cream bg, remove NFC banner, add iOS App Store section v2.4.0"
git push
```

When completely finished, run: openclaw system event --text "Done: RewardsApp v2.4.0 built and pushed" --mode now
