# Task: v2.5.0 — Add app promo image to waiting screen

In injectBrandingOverrides() in MainActivity.java, add an app promo image to the waiting screen.

## Change: Add promo image below logo on waiting screen

After injecting the logo wrap, add this image below the logo on the waiting screen only (inside `.reward-display.waiting`):

```javascript
var waitingDisplay = document.querySelector('.reward-display.waiting');
if (waitingDisplay && !document.getElementById('app-promo-img')) {
    var promoImg = document.createElement('img');
    promoImg.id = 'app-promo-img';
    promoImg.src = 'https://staff.trailscoffee.com/app-promo.jpg';
    promoImg.alt = 'Download Trails Coffee App';
    promoImg.style.cssText = 'width:100%;max-width:320px;border-radius:20px;margin:20px auto 0;display:block;box-shadow:0 8px 30px rgba(0,0,0,0.3);';
    // Insert it after the waiting message paragraphs, before the ios-waiting-section
    var iosWait = document.getElementById('ios-waiting-section');
    if (iosWait) {
        waitingDisplay.insertBefore(promoImg, iosWait);
    } else {
        waitingDisplay.appendChild(promoImg);
    }
}
```

Also remove the ios-waiting-section from the waiting screen (the text-based one) since the image already shows the App Store CTA — no need to duplicate it. So remove the ios-waiting-section injection code for the waiting screen only (keep the one below the QR on the reward screen).

## Bump version
app/build.gradle: versionCode 15, versionName "2.5.0"

## Commit and push
```bash
git add -A
git commit -m "feat: add app promo image to waiting screen v2.5.0"
git push
```

When completely finished, run: openclaw system event --text "Done: RewardsApp v2.5.0 app promo image added" --mode now
