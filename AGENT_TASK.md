# Task: v2.6.0 — Make promo image fill entire waiting card

The goal: the waiting screen card should be ONLY the logo + full-width promo image. No text, no icon, no "Waiting for next customer..." — just the image taking up the whole card.

## Changes to injectBrandingOverrides() in MainActivity.java

### 1. Hide all existing waiting card content except the card itself

```javascript
// Hide waiting screen text content
var waitingIcon = document.querySelector('.waiting-icon');
if (waitingIcon) waitingIcon.style.display = 'none';

var waitingMsg = document.querySelector('.waiting-message');
if (waitingMsg) waitingMsg.style.display = 'none';

// Hide all <p> tags inside the waiting card
var waitingDisplay = document.querySelector('.reward-display.waiting');
if (waitingDisplay) {
    waitingDisplay.querySelectorAll('p').forEach(function(p) {
        p.style.display = 'none';
    });
}
```

### 2. Make the promo image fill the full card width with no padding

Replace the current promo image injection with:

```javascript
var waitingDisplay = document.querySelector('.reward-display.waiting');
if (waitingDisplay && !document.getElementById('app-promo-img')) {
    // Remove card padding so image goes edge to edge
    waitingDisplay.style.padding = '0';
    waitingDisplay.style.overflow = 'hidden';
    waitingDisplay.style.borderRadius = '20px';

    var promoImg = document.createElement('img');
    promoImg.id = 'app-promo-img';
    promoImg.src = 'https://staff.trailscoffee.com/app-promo.jpg';
    promoImg.alt = 'Download Trails Coffee App';
    promoImg.style.cssText = 'width:100%;height:auto;display:block;border-radius:0;margin:0;';
    waitingDisplay.appendChild(promoImg);
}
```

### 3. Also hide the status bar ("Auto-refresh: 10 seconds | Timeframe: 60 minutes")

```javascript
var statusBar = document.querySelector('.status-bar');
if (statusBar) statusBar.style.display = 'none';
```

### 4. Also hide the refresh button at the bottom

```javascript
document.querySelectorAll('.refresh-button').forEach(function(btn) {
    btn.style.display = 'none';
});
```

### 5. Also hide the footer ("Page refreshes automatically every X seconds")

```javascript
var footer = document.querySelector('.footer');
if (footer) footer.style.display = 'none';
```

### 6. Keep the logo wrap at top (outside the card, in .container)

The logo stays as-is in the container above the card. No changes needed there.

## Bump version
app/build.gradle: versionCode 16, versionName "2.6.0"

## Commit and push
```bash
git add -A
git commit -m "feat: full-width promo image on waiting screen, hide status bar and footer v2.6.0"
git push
```

When completely finished, run: openclaw system event --text "Done: RewardsApp v2.6.0 full-width waiting image" --mode now
