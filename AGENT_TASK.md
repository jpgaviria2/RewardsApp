# Task: v2.7.0 — Remove white card background on waiting screen

The white area showing around the promo image is the .reward-display.waiting card background.
Fix: make the card fully transparent with no padding, no shadow, no background — just the image.

## Changes to injectBrandingOverrides() in MainActivity.java

### 1. Make the waiting card transparent and remove all spacing

```javascript
var waitingDisplay = document.querySelector('.reward-display.waiting');
if (waitingDisplay) {
    waitingDisplay.style.background = 'transparent';
    waitingDisplay.style.boxShadow = 'none';
    waitingDisplay.style.padding = '0';
    waitingDisplay.style.margin = '0';
    waitingDisplay.style.borderRadius = '0';
    waitingDisplay.style.border = 'none';
}
```

### 2. Also make the container have no gap between logo and image

```javascript
var container = document.querySelector('.container');
if (container) {
    container.style.padding = '0';
    container.style.width = '100%';
    container.style.maxWidth = '100%';
}
```

### 3. Make the logo wrap margin smaller

```javascript
var logoWrap = document.getElementById('trails-logo-wrap');
if (logoWrap) {
    logoWrap.style.marginBottom = '12px';
    logoWrap.style.marginTop = '16px';
}
```

### 4. Make the promo image fully rounded to match the card style (or square, no radius)

The image already has border-radius:0 — keep that. But also make sure the image width is 100vw essentially:

```javascript
var promoImg = document.getElementById('app-promo-img');
if (promoImg) {
    promoImg.style.width = '100%';
    promoImg.style.maxWidth = '100%';
    promoImg.style.borderRadius = '16px';
    promoImg.style.display = 'block';
}
```

Give it a 16px border radius to look polished since there's no card wrapping it anymore.

## Bump version
app/build.gradle: versionCode 17, versionName "2.7.0"

## Commit and push
```bash
git add -A
git commit -m "fix: transparent waiting card — no white background showing v2.7.0"
git push
```

When completely finished, run: openclaw system event --text "Done: RewardsApp v2.7.0 white area fixed" --mode now
