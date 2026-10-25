package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisualRewardsExperienceSourceTest {
    private static String source(String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(relative)), StandardCharsets.UTF_8);
    }

    @Test public void tutorialIsBundledLocallyWithStaticFallback() throws Exception {
        String controller=source("src/main/java/com/bitcoinrewards/nfcdisplay/WalletTutorialVideoController.java");
        String layout=source("src/main/res/layout/activity_main.xml");
        Path video=Paths.get("src/main/res/raw/wallet_rewards_tutorial.mp4");
        Path poster=Paths.get("src/main/res/drawable-nodpi/wallet_rewards_tutorial_poster.jpg");
        assertTrue(Files.isRegularFile(video));
        assertTrue(Files.size(video)<400_000L);
        assertTrue(Files.isRegularFile(poster));
        assertTrue(controller.contains("openRawResourceFd(R.raw.wallet_rewards_tutorial)"));
        assertFalse(controller.contains("setDataSource(\"http"));
        assertTrue(layout.contains("@drawable/wallet_rewards_tutorial_poster"));
        int textureStart=layout.indexOf("<TextureView");
        int textureEnd=layout.indexOf("/>",textureStart);
        assertTrue(textureStart>=0&&textureEnd>textureStart);
        assertFalse(layout.substring(textureStart,textureEnd).contains("android:background="));
        assertTrue(layout.indexOf("@drawable/wallet_rewards_tutorial_poster")>textureEnd);
        assertTrue(layout.contains("android:id=\"@+id/wallet_tutorial_poster\""));
        assertTrue(controller.contains("onSurfaceTextureUpdated"));
        assertTrue(controller.contains("poster.setVisibility(View.GONE)"));
        assertTrue(controller.contains("poster.setVisibility(View.VISIBLE)"));
        assertTrue(controller.contains("showPoster();\n        if (player != null)"));
    }

    @Test public void captureOverlaysConsumeTouchesAndAuthenticationFailureRevealsGuidance() throws Exception {
        String main=source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        String layout=source("src/main/res/layout/activity_main.xml");
        assertTrue(layout.contains("android:id=\"@+id/nfc_tap_overlay\""));
        assertTrue(layout.contains("android:id=\"@+id/nfc_reader_overlay\""));
        assertTrue(count(layout,"android:clickable=\"true\"")>=2);
        assertTrue(count(layout,"android:focusable=\"true\"")>=2);
        assertTrue(main.contains("if (loginExpired) {\n                inlineAuthenticationFailure = InlineCheckInAuthentication.failureMessage(responseCode);\n                hideCaptureOverlays();"));
        assertTrue(main.contains("if (!inlineSubmissionInFlight) hideCaptureOverlays();"));
        assertTrue(main.contains("inlineSubmissionInFlight || captureConfirmationVisible"));
    }

    @Test public void scannerIsFullWidthAndTutorialOnlyOccupiesIdleLowerArea() throws Exception {
        String main=source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        String layout=source("src/main/res/layout/activity_main.xml");
        String strings=source("src/main/res/values/strings.xml").toLowerCase();
        int scanner=layout.indexOf("android:id=\"@+id/inline_rewards_scanner\"");
        int tutorialPanel=layout.indexOf("android:id=\"@+id/idle_tutorial_panel\"");
        int tutorialVideo=layout.indexOf("android:id=\"@+id/wallet_tutorial_video\"");
        assertTrue(scanner>=0&&tutorialPanel>scanner&&tutorialVideo>tutorialPanel);
        assertTrue(layout.contains("android:layout_height=\"410dp\""));
        assertTrue(layout.contains("android:layout_width=\"176dp\""));
        assertTrue(layout.contains("android:layout_height=\"391dp\""));
        assertTrue(layout.contains("android:id=\"@+id/wallet_tutorial_frame\""));
        assertTrue(main.contains("document.querySelector('#rewards-display-state')"));
        assertTrue(main.contains("state==='checked-in'"));
        assertTrue(main.contains("idleTutorialEligible = explicitWaiting || (!hasExplicitState && !rewardVisible && !checkedIn)"));
        assertTrue(main.contains("idleTutorialPanel.setVisibility(idleTutorialEligible ? View.VISIBLE : View.GONE)"));
        assertTrue(main.contains("applyResponsiveExperienceLayout"));
        assertTrue(main.contains("availableHeight * 0.45f"));
        assertTrue(main.contains("tutorialHeight * 288f / 640f"));
        assertFalse(strings.contains("bitcoin"));
        assertTrue(strings.contains("get rewarded in sats"));
    }

    private static int count(String value,String needle){int count=0,offset=0;while((offset=value.indexOf(needle,offset))>=0){count++;offset+=needle.length();}return count;}
}
