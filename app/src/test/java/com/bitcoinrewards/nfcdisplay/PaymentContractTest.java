package com.bitcoinrewards.nfcdisplay;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentContractTest {
    private static final String ID = "cd829d5a-7652-4f2a-bcff-698132447685";
    private JSONObject invoice(String status) throws Exception {
        return new JSONObject().put("operationId", ID).put("invoiceId", "invoice-1")
            .put("currency", "CAD").put("amountMinor", 1234).put("status", status)
            .put("bolt11", "lnbc123456789012345678901234567890").put("expiresAt", "2026-11-07T15:00:00Z");
    }
    private void reject(JSONObject json, String previous) throws Exception {
        try { new PaymentContract.Invoice(json, ID, 1234, previous); fail("Accepted invalid echo"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void nativeKeypadUsesIntegerCentsOnly() {
        assertEquals(1234, PaymentContract.amount("1234"));
        assertEquals("CAD $12.34", PaymentContract.money(1234));
        for (String value : new String[]{"", "0", "-1", "1.00", "1e3", "100000000", " 100", "１２"}) {
            try { PaymentContract.amount(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void settlementOnlyRecognizesServerTerminalStates() throws Exception {
        for (String status : new String[]{"pending", "review", "paid", "cancelled", "expired"}) {
            assertEquals(status, new PaymentContract.Invoice(invoice(status), ID, 1234, null).status);
        }
        assertFalse(PaymentContract.terminal("pending"));
        assertFalse(PaymentContract.terminal("review"));
        assertFalse(PaymentContract.terminal("OK"));
        assertTrue(PaymentContract.terminal("paid"));
    }
    @Test public void reviewWithNoCreatedInvoiceIsSafeAndTerminalMayOmitQr() throws Exception {
        JSONObject review = invoice("review").put("invoiceId", JSONObject.NULL).put("bolt11", JSONObject.NULL).put("expiresAt", JSONObject.NULL);
        PaymentContract.Invoice result = new PaymentContract.Invoice(review, ID, 1234, null);
        assertEquals("review", result.status); assertNull(result.invoiceId); assertEquals("", result.bolt11);
        for (String status : new String[]{"cancelled", "expired"}) {
            assertEquals(status, new PaymentContract.Invoice(invoice(status).put("bolt11", JSONObject.NULL), ID, 1234, "invoice-1").status);
        }
    }
    @Test public void capabilityRequiresVersionedCadAvailability() throws Exception {
        assertTrue(PaymentContract.capable(new JSONObject("{\"available\":true,\"currency\":\"CAD\",\"version\":1}")));
        for (String value : new String[]{"{}", "{\"available\":false,\"currency\":\"CAD\",\"version\":1}",
            "{\"available\":true,\"currency\":\"USD\",\"version\":1}", "{\"available\":\"true\",\"currency\":\"CAD\",\"version\":1}"}) {
            assertFalse(PaymentContract.capable(new JSONObject(value)));
        }
    }
    @Test public void responsesRejectDuplicatesNestingAndTrailingJunk() {
        for (String value : new String[]{"{\"status\":\"paid\",\"status\":\"pending\"}", "{\"x\":{}}", "{\"x\":[]}", "{} garbage"}) {
            try { PaymentContract.response(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
        assertEquals("pending", PaymentContract.response("{\"status\":\"pending\"}").optString("status"));
    }
    @Test public void rejectsDifferentOperationAmountCurrencyAndInvoice() throws Exception {
        reject(invoice("pending").put("operationId", "wrong"), null);
        reject(invoice("pending").put("currency", "USD"), null);
        reject(invoice("pending").put("amountMinor", 1235), null);
        reject(invoice("pending").put("amountMinor", 1234.0), null);
        reject(invoice("pending").put("amountMinor", "1234"), null);
        reject(invoice("pending"), "invoice-2");
        reject(invoice("settled"), null);
        reject(invoice("pending").put("bolt11", "https://evil.example"), null);
    }
}
