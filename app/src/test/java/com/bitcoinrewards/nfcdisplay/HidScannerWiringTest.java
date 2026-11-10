package com.bitcoinrewards.nfcdisplay;
import org.junit.Test;
import java.nio.file.*;
import static org.junit.Assert.*;

public class HidScannerWiringTest {
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/" + name + ".java")), java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test public void reachableMainScreenRoutesHidAndCameraToSameGuardedSubmission() throws Exception {
        String s = source("MainActivity");
        assertTrue("MainActivity has no HID dispatch", s.contains("boolean dispatchKeyEvent(KeyEvent event)"));
        assertTrue(s.contains("hidScanner.handle(event, canAcceptCustomerScan(), this::acceptHidWalletScan)"));
        assertTrue(s.indexOf("hidScanner.handle(event") < s.indexOf("super.dispatchKeyEvent(event)"));
        assertTrue(s.contains("WalletQrPayloadParser.parse(raw)"));
        assertTrue(s.contains("private void submitInlineWalletPayload(WalletQrPayloadParser.ParsedPayload payload)"));
        assertTrue(s.contains("if (!canAcceptCustomerScan()) return;"));
        assertTrue(s.contains("inlineAuthenticationFailure == null"));
        assertTrue(s.contains("!captureConfirmationVisible"));
        assertTrue(s.contains("hidScanner.reset()"));
        String input = source("HidScannerInput");
        assertTrue(input.contains("event.getUnicodeChar(event.getMetaState())"));
        assertFalse(input.contains("requestPermissions"));
        assertFalse(input.contains("confirm("));
        assertTrue(input.contains("device.isVirtual()"));
        assertTrue(input.contains("device.isExternal()"));
    }
    @Test public void provisionedScannerSharesCoordinatorAndConsumesDialogScannerKeys() throws Exception {
        String s = source("RewardsProfileScanActivity");
        assertTrue(s.contains("hidScanner.handle(event, canAcceptCustomerScan(), this::acceptHidWalletScan)"));
        assertTrue(s.contains("private void beginWalletPayload(WalletQrPayloadParser.ParsedPayload payload)"));
        assertTrue(s.contains("coordinator.begin(payload)"));
        assertTrue(s.contains(".setOnKeyListener((dialog, keyCode, event) -> hidScanner.handle(event, false, raw -> { }))"));
        assertTrue(s.contains("hidScanner.reset()"));
    }
    @Test public void authenticationGuidancePrecedesCameraFallbackWithoutGatingHid() throws Exception {
        String s = source("MainActivity");
        String update = s.substring(s.indexOf("private void updateInlineScannerState()"), s.indexOf("private void updateExperienceForPage"));
        assertTrue("authentication failure must not be masked by denied camera", update.indexOf("if (inlineAuthenticationFailure != null)") < update.indexOf("if (!hasCameraPermission())"));
        String permissionCallback = s.substring(s.indexOf("@Override public void onRequestPermissionsResult"), s.indexOf("private void updateInlineScannerState()"));
        assertFalse("permission callback must use the same status precedence", permissionCallback.contains("setRewardsScannerStatus("));
        String admission = s.substring(s.indexOf("private boolean canAcceptCustomerScan()"), s.indexOf("@Override public boolean dispatchKeyEvent"));
        assertFalse("HID admission independent of camera", admission.contains("hasCameraPermission"));
    }
    @Test public void everyNonCustomerSurfaceConsumesScannerBeforeFocusedControls() throws Exception {
        for (String name : new String[]{"PaymentActivity", "NativePosActivity", "SettingsActivity", "LoginCodeScanActivity"}) {
            String s = source(name);
            assertTrue(name + " missing consume-only guard", s.contains("hidScanner.handle(event, false, raw -> { })"));
            assertTrue(name + " guard must precede focused controls", s.indexOf("hidScanner.handle(event") < s.indexOf("super.dispatchKeyEvent(event)"));
        }
    }
    @Test public void everyAppOwnedDialogConsumesScannerIndependentlyOfActivityDispatch() throws Exception {
        for (String name : new String[]{"PaymentActivity", "SettingsActivity", "RewardsProfileScanActivity"}) {
            String s = source(name);
            int dialogs = s.split("new AlertDialog.Builder", -1).length - 1;
            int guards = s.split(java.util.regex.Pattern.quote(".setOnKeyListener((dialog, keyCode, event) -> hidScanner.handle(event, false, raw -> { }))"), -1).length - 1;
            assertTrue(name + " inventory has dialogs", dialogs > 0);
            assertEquals(name + " every dialog needs its own scanner guard", dialogs, guards);
        }
    }
    @Test public void devicePolicyRejectsVirtualAdbSoftKeyboardAndOtherKeyboards() throws Exception {
        Class<?> type;
        try { type=Class.forName("com.bitcoinrewards.nfcdisplay.HidScannerInput"); }
        catch (ClassNotFoundException e) { fail("Missing scanner device policy"); return; }
        java.lang.reflect.Method method=type.getDeclaredMethod("acceptsDevice", int.class,int.class,int.class,boolean.class,boolean.class,boolean.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(null,1,0x045e,0x0040,true,false,true));
        assertEquals(false, method.invoke(null,-1,0x045e,0x0040,true,false,true));
        assertEquals(false, method.invoke(null,1,0x045e,0x0040,true,true,true));
        assertEquals(false, method.invoke(null,1,0x045e,0x0040,false,false,true));
        assertEquals(false, method.invoke(null,1,0x045e,0x0040,true,false,false));
        assertEquals(false, method.invoke(null,1,0x1234,0x0040,true,false,true));
        assertEquals(false, method.invoke(null,1,0x045e,0x0041,true,false,true));
    }
}
