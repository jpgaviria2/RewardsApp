# Task: Local HTML Serving for RewardsApp

Rewrite RewardsApp to serve local Trails-branded HTML instead of loading the BTCPay WebView URL.

## Goal
- App polls BTCPay API every 5 seconds for pending rewards
- WebView loads LOCAL HTML from assets (not remote URL)
- No reward pending → show waiting.html (brown gradient, ☕ icon)
- Reward arrives → show reward.html (full Trails template with sats + QR)

## Step 1: Create app/src/main/assets/waiting.html

Full Trails-branded waiting screen:
- Brown gradient background (linear-gradient 135deg, #6B4423 → #CD853F)
- Cream card (#FFFEF7), 24dp border radius, large shadow
- ☕ icon (72px) with pulse animation
- "Trails Coffee Rewards" heading in Playfair Display, #6B4423
- "Waiting for next customer..." subtitle
- "Trails Coffee • Anmore, BC" footer
- Google Fonts: Playfair Display + Inter

## Step 2: Create app/src/main/assets/reward.html

Full Trails-branded reward screen matching the HTML template provided:
- Same brown gradient background
- Cream card with slide-up animation
- "Coffee Rewards Earned!" heading in Playfair Display
- Amount section: cream bg, brown border, large sats amount (#sats-amount), "satoshis" unit
- QR image: white card, shadow, <img id="qr-image">
- Instructions: cream bg, brown left border, "How to Collect:" 3 steps
- App promo: dark brown gradient card, white text, "Don't have the app yet?"
- Countdown: <div class="countdown">Auto-closing in <span id="countdown-seconds">30</span> seconds</div>
- Done button: brown gradient, full width, calls AndroidBridge.dismiss()
- Hidden NFC data: <div id="nfc-lnurl-data" data-lnurl="" data-reward-id="" style="display:none"></div>
- Footer: "Trails Coffee • Anmore, BC"

JS in reward.html:
```javascript
let seconds = 30;
const el = document.getElementById("countdown-seconds");
const timer = setInterval(() => {
    seconds--;
    el.textContent = seconds;
    if (seconds <= 0) { clearInterval(timer); AndroidBridge.dismiss(); }
}, 1000);

function loadReward(satsAmount, qrDataUri, lnurl, rewardId) {
    document.getElementById("sats-amount").textContent = parseInt(satsAmount).toLocaleString();
    document.getElementById("qr-image").src = qrDataUri;
    document.getElementById("nfc-lnurl-data").dataset.lnurl = lnurl;
    document.getElementById("nfc-lnurl-data").dataset.rewardId = rewardId;
}
```

## Step 3: Create RewardPoller.java

File: app/src/main/java/com/bitcoinrewards/nfcdisplay/RewardPoller.java

Polls GET {btcpayUrl}/plugins/bitcoin-rewards/{storeId}/display-data every 5 seconds.
Auth: Authorization: Bearer {apiKey}

Response JSON:
```json
{"hasReward":true,"rewardId":"abc","rewardAmountSatoshis":150,"lnurlString":"lnurl1...","lnurlQrDataUri":"data:image/png;base64,...","remainingSeconds":28}
```

Interface:
```java
public interface RewardCallback {
    void onReward(String rewardId, long sats, String lnurl, String qrDataUri, int remainingSeconds);
    void onNoReward();
}
```

Use Handler + Runnable for polling. Stop on destroy(). Catch all exceptions silently, call onNoReward on error.

## Step 4: Rewrite MainActivity.java

Key changes:
1. On launch: webView.loadUrl("file:///android_asset/waiting.html") — NOT the remote BTCPay URL
2. Create RewardPoller with stored prefs (btcpay_url, store_id, api_key)
3. Track current state: boolean showingReward, String currentRewardId
4. onReward() callback (run on main thread via Handler):
   - If not already showing this reward: load file:///android_asset/reward.html
   - In onPageFinished (when URL contains "reward.html"): call JS loadReward(sats, qrDataUri, lnurl, rewardId)
   - Also call setNfcPayload("lightning:" + lnurl)
5. onNoReward() callback (run on main thread):
   - If showingReward == true: load waiting.html, clearNfcPayload(), showingReward = false
6. Add public void dismissCurrentReward() method:
   - POST to {btcpayUrl}/plugins/bitcoin-rewards/{storeId}/dismiss-reward with {"rewardId": currentRewardId}
   - Then load waiting.html, clearNfcPayload(), showingReward = false
   - Do network call in background thread
7. Keep all existing NFC HCE code exactly as-is (setNfcPayload, clearNfcPayload, onNfcTapDetected, onResume, onPause)
8. Keep settings gear button
9. Keep screen-on flag (FLAG_KEEP_SCREEN_ON)
10. Keep NFC tap overlay feedback

Remove: performBackgroundLogin(), LoginTask, all cookie/session code (no longer needed)

## Step 5: Update AndroidBridge.java

Add:
```java
@JavascriptInterface
public void dismiss() {
    activity.dismissCurrentReward();
}
```

Where activity is a reference to MainActivity passed in constructor.

Check existing AndroidBridge.java constructor signature and add the dismiss() method.

## Step 6: Update app/build.gradle

versionCode 11, versionName "2.1.0"

## Step 7: Commit and push

```bash
git add -A
git commit -m "feat: local Trails HTML — no remote WebView, polls BTCPay API directly"
git push
```

When completely finished, run: openclaw system event --text "Done: RewardsApp v2.1.0 local HTML serving built and pushed" --mode now
