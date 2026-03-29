# Task: v2.3.0 — Full DOM injection for Trails branding

Enhance the JS/CSS injection in MainActivity.java to do full DOM manipulation, not just CSS overrides.

## Changes to injectBrandingOverrides() in MainActivity.java

Replace the current injectBrandingOverrides() method with a new version that does both CSS + DOM manipulation.

The JS should:

### 1. Add Trails logo at the top of the container
Insert a logo image before the .header div (or as first child of .container):
```javascript
var container = document.querySelector('.container');
if (container && !document.getElementById('trails-logo')) {
    var logo = document.createElement('img');
    logo.id = 'trails-logo';
    logo.src = 'https://trailscoffee.com/LOGO-BROWN.png';
    logo.alt = 'Trails Coffee';
    logo.style.cssText = 'width:180px;max-width:60%;margin-bottom:20px;display:block;margin-left:auto;margin-right:auto;';
    container.insertBefore(logo, container.firstChild);
}
```

### 2. Replace all instances of "Bitcoin Rewards" text with "Coffee Rewards"
```javascript
document.querySelectorAll('h1, h2, h3, p, div, span, button').forEach(function(el) {
    if (el.childNodes.length === 1 && el.childNodes[0].nodeType === 3) {
        el.textContent = el.textContent
            .replace(/Bitcoin Rewards Display/g, 'Trails Coffee Rewards')
            .replace(/Bitcoin Rewards/g, 'Coffee Rewards')
            .replace(/Bitcoin-backed rewards/gi, 'Coffee rewards')
            .replace(/⏳/g, '☕')
            .replace(/Waiting for rewards\.\.\./g, 'Waiting for next customer...')
            .replace(/The latest unclaimed reward will appear here automatically/g, 'Rewards appear here automatically after payment')
            .replace(/Page refreshes automatically every/g, 'Updates every')
            .replace(/Back to Settings/g, '');
    }
});
```

### 3. Update page title
```javascript
document.title = 'Trails Coffee Rewards';
```

### 4. Remove "Back to Settings" link entirely
```javascript
var links = document.querySelectorAll('a');
links.forEach(function(a) {
    if (a.textContent.includes('Settings') || a.textContent.includes('Back to')) {
        a.parentElement.style.display = 'none';
    }
});
```

### 5. Fix the header h1 text
```javascript
var h1 = document.querySelector('.header h1');
if (h1) h1.textContent = '☕ Trails Coffee Rewards';
var headerP = document.querySelector('.header p');
if (headerP) headerP.textContent = 'Anmore, BC';
```

### 6. Fix waiting message
```javascript
var waitingMsg = document.querySelector('.waiting-message');
if (waitingMsg) waitingMsg.textContent = 'Waiting for next customer...';
var waitingIcon = document.querySelector('.waiting-icon');
if (waitingIcon) waitingIcon.textContent = '☕';
```

### 7. Keep all existing CSS overrides from the current injectBrandingOverrides() method

## Full updated injectBrandingOverrides() + escapeForJs()

Write a clean combined method that does all the above in one evaluateJavascript call.
Use a single large JS string, wrapping everything in an IIFE: (function() { ... })()
Guard with: if (document.getElementById('trails-injected')) return; then set document.getElementById or add a marker element.

## Bump version
app/build.gradle: versionCode 13, versionName "2.3.0"

## Commit and push
```bash
git add -A
git commit -m "feat: full DOM injection — logo, text cleanup, remove Bitcoin branding v2.3.0"
git push
```

When completely finished, run: openclaw system event --text "Done: RewardsApp v2.3.0 full DOM injection built and pushed" --mode now
