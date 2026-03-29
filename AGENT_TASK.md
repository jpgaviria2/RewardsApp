# Task: v2.8.0 — Fit waiting screen in viewport

The header text ("☕ Trails Coffee Rewards" + "Anmore, BC") is taking up too much space and pushing the promo image off screen. Fix: hide the header text, shrink the logo, and constrain the image to fit the screen.

## Changes to injectBrandingOverrides() in MainActivity.java

### 1. Hide the header h1 and subtitle text on the waiting screen only

```javascript
// Only hide header text when on waiting screen (not reward screen)
if (document.querySelector('.reward-display.waiting')) {
    var headerH1 = document.querySelector('.header h1');
    if (headerH1) headerH1.style.display = 'none';
    var headerP = document.querySelector('.header p');
    if (headerP) headerP.style.display = 'none';
}
```

### 2. Shrink the logo wrap

```javascript
var logoWrap = document.getElementById('trails-logo-wrap');
if (logoWrap) {
    logoWrap.style.padding = '10px 16px';
    logoWrap.style.marginBottom = '8px';
    logoWrap.style.marginTop = '8px';
}
var logoImg = logoWrap ? logoWrap.querySelector('img') : null;
if (logoImg) {
    logoImg.style.width = '100px';
}
```

### 3. Make the promo image fill remaining viewport height

```javascript
var promoImg = document.getElementById('app-promo-img');
if (promoImg) {
    promoImg.style.width = '100%';
    promoImg.style.maxWidth = '100%';
    promoImg.style.maxHeight = '75vh';
    promoImg.style.objectFit = 'cover';
    promoImg.style.objectPosition = 'top';
    promoImg.style.borderRadius = '16px';
    promoImg.style.display = 'block';
}
```

### 4. Make body not scroll on waiting screen

```javascript
if (document.querySelector('.reward-display.waiting')) {
    document.body.style.overflow = 'hidden';
    document.body.style.height = '100vh';
}
```

### 5. Make container use flexbox to fill height properly

```javascript
var container = document.querySelector('.container');
if (container && document.querySelector('.reward-display.waiting')) {
    container.style.display = 'flex';
    container.style.flexDirection = 'column';
    container.style.alignItems = 'center';
    container.style.minHeight = '100vh';
    container.style.justifyContent = 'flex-start';
    container.style.paddingTop = '0';
}
```

## Bump version
app/build.gradle: versionCode 18, versionName "2.8.0"

## Commit and push
```bash
git add -A
git commit -m "fix: hide header text on waiting screen, shrink logo, image fits viewport v2.8.0"
git push
```

When completely finished, run: openclaw system event --text "Done: RewardsApp v2.8.0 waiting screen fits viewport" --mode now
