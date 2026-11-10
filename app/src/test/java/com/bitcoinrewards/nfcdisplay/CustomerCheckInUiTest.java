package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import static org.junit.Assert.*;

public class CustomerCheckInUiTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    @Test public void celebrationOwnsBoundedConfettiAndClearsItOnDetach() throws Exception {
        String layout = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/res/layout/activity_main.xml")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(layout.contains("com.bitcoinrewards.nfcdisplay.CheckInConfettiView"));
        String confetti = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/CheckInConfettiView.java")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(confetti.contains("ValueAnimator.ofFloat(0f, 1f)"));
        assertTrue(confetti.contains("animator.setDuration(1_400L)"));
        assertTrue(confetti.contains("@Override protected void onDetachedFromWindow()"));
        assertTrue(confetti.contains("animator.cancel()"));
        assertFalse(confetti.contains("INFINITE"));
    }
    @Test public void confirmationPresentationIsBoundToResponseNotQrAndClearedOnPause() throws Exception {
        String main = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("Only authoritative confirmed IDs can enter the celebration", main.contains("celebration.confirm(confirmedCheckInId, confirmedCustomerHandle, requestEpoch)"));
        String completion = main.substring(main.indexOf("@Override protected void onPostExecute(Boolean ok)"));
        assertFalse("A wallet label is not a person's name", completion.contains("String label = boltcardUrl"));
        assertTrue(completion.contains("String label = celebration.name()"));
        String pause = main.substring(main.indexOf("@Override protected void onPause()"), main.indexOf("@Override protected void onDestroy()"));
        assertTrue(pause.contains("hideCaptureOverlays();"));
        assertTrue(main.contains("successOverlay.setText(\"\")"));
        assertTrue(main.contains("confettiView.stop();"));
        String hide = main.substring(main.indexOf("private void hideCaptureOverlays()"), main.indexOf("private void pulseTapCard()"));
        assertTrue("Status must not retain a previous customer's name", hide.contains("R.string.scanner_ready"));
        assertTrue(main.contains("CheckInCelebration.isConfirmedHttpStatus(responseCode)"));
        assertTrue("Presentation must not tighten the financial capture contract", main.contains("ConfirmedCheckInResponse.confirmedId(responseBytes)"));
        assertTrue("Late responses must not celebrate on another page or a blocked financial surface", completion.contains("if (!canAcceptCustomerScan() || requestEpoch != captureEpoch)"));
    }
    @Test public void customerIdleReplacesCameraCutoutWithProminentScanHere() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document layout = factory.newDocumentBuilder().parse("src/main/res/layout/activity_main.xml");
        Element prompt = null;
        NodeList nodes = layout.getElementsByTagName("TextView");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if ("@+id/scan_here_prompt".equals(element.getAttributeNS(ANDROID, "id"))) prompt = element;
        }
        assertNotNull("Idle must have a Scan here prompt instead of the camera cutout", prompt);
        assertEquals("@string/scan_here", prompt.getAttributeNS(ANDROID, "text"));
        assertEquals("48sp", prompt.getAttributeNS(ANDROID, "textSize"));
        assertEquals("match_parent", prompt.getAttributeNS(ANDROID, "layout_height"));
        String strings = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/res/values/strings.xml")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(strings.contains("Hold QR toward the scanner"));
        assertFalse(new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/res/layout/activity_main.xml")), java.nio.charset.StandardCharsets.UTF_8).contains("@string/camera_guide"));
        NodeList buttons = layout.getElementsByTagName("Button");
        for (int i = 0; i < buttons.getLength(); i++) {
            Element button = (Element) buttons.item(i);
            if ("@+id/btn_flip_inline_camera".equals(button.getAttributeNS(ANDROID, "id"))) {
                assertEquals("gone", button.getAttributeNS(ANDROID, "visibility"));
            }
        }
    }
}
