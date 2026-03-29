# Task: Fix RewardsApp — Revert to WebView + CSS injection

The previous rewrite broke the app by polling a non-existent API endpoint (`/display-data`).
The real endpoint is `/plugins/bitcoin-rewards/{storeId}/display` which returns HTML, not JSON.

## What to do

Revert MainActivity.java back to the original WebView approach (loading the remote BTCPay display URL),
but add CSS injection in onPageFinished to replace purple with Trails brown branding.

## Step 1: Restore MainActivity.java to WebView approach

The original flow was:
1. Load display URL: `{btcpayUrl}/plugins/bitcoin-rewards/{storeId}/display` in WebView
2. Background login to get session cookie first (LoginTask)
3. onPageFinished: extractLnurlFromPage + injectNfcBanner

Keep all of that. Just ADD a CSS injection call in onPageFinished BEFORE extractLnurlFromPage.

## Step 2: Add injectBrandingOverrides() method to MainActivity.java

Add this method:

```java
private void injectBrandingOverrides(WebView view) {
    String css =
        "body { background: linear-gradient(135deg, #6B4423 0%, #CD853F 100%) !important; }" +
        ".header h1 { color: #FFFEF7 !important; }" +
        ".header p { color: rgba(255,255,255,0.9) !important; }" +
        ".status-bar { background: rgba(255,255,255,0.2) !important; }" +
        ".reward-display { background: #FFFEF7 !important; }" +
        ".reward-display.waiting { background: #FFFEF7 !important; }" +
        ".waiting-message { color: #6B4423 !important; }" +
        ".waiting-icon { font-size: 4rem; }" +
        ".amount { color: #28a745 !important; }" +
        "h2[style*='color'] { color: #6B4423 !important; }" +
        ".countdown-timer { color: #6B4423 !important; }" +
        ".countdown-timer.warning { color: #CD853F !important; }" +
        ".refresh-button { color: #6B4423 !important; }" +
        ".refresh-button:hover { background: #6B4423 !important; color: white !important; }" +
        ".done-button { background: linear-gradient(135deg, #6B4423, #CD853F) !important; border-color: #6B4423 !important; }" +
        "#nfc-tap-btn { background: linear-gradient(135deg, #6B4423 0%, #CD853F 100%) !important; }" +
        "a[style*='color: white'] { color: rgba(255,255,255,0.8) !important; }";

    String js = "(function() {" +
        "var style = document.createElement('style');" +
        "style.id = 'trails-branding';" +
        "if (document.getElementById('trails-branding')) return;" +
        "style.textContent = " + escapeForJs(css) + ";" +
        "document.head.appendChild(style);" +
        "})()";

    view.evaluateJavascript(js, null);
}

private String escapeForJs(String s) {
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
}
```

## Step 3: Call injectBrandingOverrides in onPageFinished

In the WebViewClient onPageFinished, add this call right after hiding the loading overlay:

```java
injectBrandingOverrides(view);
```

Call it before extractLnurlFromPage and injectNfcBanner.

## Step 4: Delete RewardPoller.java and the asset HTML files

Delete:
- app/src/main/java/com/bitcoinrewards/nfcdisplay/RewardPoller.java
- app/src/main/assets/waiting.html (if exists)
- app/src/main/assets/reward.html (if exists)

Restore the original MainActivity flow — load remote URL, background login, CSS injection on page load.

The original MainActivity.java is in git history. Use git to restore the original if needed, or rewrite it based on the original structure described above.

## Step 5: Check AndroidBridge.java

Make sure AndroidBridge.java still has the original methods. Remove the dismiss() method if it references MainActivity.dismissCurrentReward() since that method won't exist anymore.

## Step 6: Bump version

app/build.gradle: versionCode 12, versionName "2.2.0"

## Step 7: Commit and push

```bash
git add -A
git commit -m "fix: revert to WebView + CSS injection for Trails branding"
git push
```

When completely finished, run: openclaw system event --text "Done: RewardsApp v2.2.0 fixed — WebView with CSS branding injection" --mode now
