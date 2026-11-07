package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

/** Wiring assertions complement transport/state tests; hardware acceptance is separate. */
public class PaymentModeSourceTest {
    private String source(String name) throws Exception { return new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/" + name + ".java")), java.nio.charset.StandardCharsets.UTF_8); }
    @Test public void separateNativePaymentFlowCannotCallRewardsOrIssuerApis() throws Exception {
        String activity = source("PaymentActivity"); String api = source("PaymentApi");
        assertTrue(activity.contains("BarcodeFormat.QR_CODE"));
        assertTrue(activity.contains("PaymentContract.amount(digits)"));
        assertFalse(activity.contains("check-in"+"/api"));
        assertFalse(activity.contains("RewardsScanCoordinator"));
        assertFalse(activity.contains("loadUrl("));
        assertFalse(activity.contains("Log.")); assertFalse(api.contains("Log."));
        assertTrue(api.contains("/pos/api"));
        assertTrue(activity.indexOf("operations.begin(") < activity.indexOf("api.request(\"POST\", \"/invoices\""));
        assertTrue(activity.indexOf("operations.markTap()") < activity.indexOf("api.tapAndRead("));
    }
    @Test public void recoveryReconcilesAndDoesNotRecreateOrReplayCard() throws Exception {
        String activity = source("PaymentActivity");
        String reconcile = activity.substring(activity.indexOf("private void reconcile()"), activity.indexOf("interface Success"));
        assertTrue(reconcile.contains("\"GET\"")); assertFalse(reconcile.contains("\"POST\""));
        assertTrue(activity.contains("operations.clearTerminal(invoice.status)"));
        assertTrue(activity.contains("operations.exists()) { Toast.makeText"));
        assertTrue(activity.contains("tapGate.pause()"));
        assertTrue(activity.contains("tapGate.allows(ticket"));
        assertTrue(activity.contains("operations.invoiceId()"));
        assertTrue(activity.contains("api.cancelAndRead(operations.id())"));
    }
    @Test public void mainStopsCameraAndRewardsNfcBeforeLaunchAndRecoversPersistedCollection() throws Exception {
        String main = source("MainActivity");
        assertTrue(main.contains("if (!scannerPageEligible || collectionBlocked())"));
        assertTrue(main.contains("activityResumed && !collectionBlocked()"));
        assertTrue(main.contains("if (collectionBlocked() || !activityResumed"));
        assertTrue(main.contains("if (!activityResumed || collectionBlocked() || result == null"));
        assertTrue(main.contains("if (new PaymentOperationStore(this).exists()) { openCollection(); return; }"));
        assertTrue(main.contains("collectionWaitingPage = explicitWaiting && !checkedIn && !rewardVisible"));
        assertTrue(main.contains("if (epoch == captureEpoch) submitBoltcardTap(boltcardUrl)"));
    }
    @Test public void preferencesContainNoCardInvoiceOrCookieSecret() throws Exception {
        String store = source("PaymentOperationStore");
        assertFalse(store.contains("bolt11")); assertFalse(store.contains("boltcardUrl"));
        assertFalse(store.contains("Cookie"));
        assertTrue(store.contains(".commit()"));
        assertFalse(store.contains(".apply()"));
    }
}
