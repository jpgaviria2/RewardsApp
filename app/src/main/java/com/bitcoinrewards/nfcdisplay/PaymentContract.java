package com.bitcoinrewards.nfcdisplay;

import org.json.JSONObject;
import java.util.UUID;

/** Strict server echo validation. Settlement is never inferred from a card callback. */
final class PaymentContract {
    static final long MAX_MINOR = 99_999_999L;
    static boolean capable(JSONObject json) {
        return "CAD".equals(json.opt("currency")) && Boolean.TRUE.equals(json.opt("available"))
            && Integer.valueOf(1).equals(json.opt("version"));
    }
    static JSONObject response(String text) {
        // This protocol is a flat object. Reject nesting before the strict duplicate-key parser.
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 1 || c == '[') throw new IllegalArgumentException(); }
            else if (c == '}' || c == ']') depth--;
        }
        return ExactJson.object(text);
    }
    static long amount(String digits) {
        if (digits == null || !digits.matches("[0-9]{1,8}")) throw new IllegalArgumentException();
        long value = Long.parseLong(digits);
        if (value <= 0 || value > MAX_MINOR) throw new IllegalArgumentException();
        return value;
    }
    static String money(long minor) {
        return String.format(java.util.Locale.CANADA, "CAD $%d.%02d", minor / 100, minor % 100);
    }
    static boolean terminal(String status) {
        return "paid".equals(status) || "expired".equals(status) || "cancelled".equals(status);
    }
    static final class Invoice {
        final String operationId, invoiceId, status, bolt11, expiresAt;
        final long amountMinor;
        Invoice(JSONObject json, String operation, long amount, String previousInvoice) throws Exception {
            UUID.fromString(operation);
            operationId = json.getString("operationId");
            status = json.getString("status");
            invoiceId = json.isNull("invoiceId") ? null : json.getString("invoiceId");
            Object rawAmount = json.get("amountMinor");
            if (!(rawAmount instanceof Integer || rawAmount instanceof Long)) throw new IllegalArgumentException();
            amountMinor = ((Number) rawAmount).longValue();
            if (!operation.equals(operationId) || !"CAD".equals(json.getString("currency"))
                || amount != amountMinor || amount <= 0 || amount > MAX_MINOR
                || (invoiceId == null ? !("review".equals(status) || "cancelled".equals(status) || "expired".equals(status)) : invoiceId.isEmpty() || invoiceId.length() > 256)
                || (previousInvoice != null && !previousInvoice.equals(invoiceId))
                || !(terminal(status) || "pending".equals(status) || "review".equals(status))) {
                throw new IllegalArgumentException("Invoice identity mismatch");
            }
            bolt11 = json.isNull("bolt11") ? "" : json.getString("bolt11");
            expiresAt = json.isNull("expiresAt") && !("pending".equals(status) || "paid".equals(status))
                ? ("review".equals(status) ? "Unavailable — staff review required" : "Not payable — server confirmed") : json.getString("expiresAt");
            if (expiresAt.isEmpty() || expiresAt.length() > 64 || bolt11.length() > 8192) throw new IllegalArgumentException();
            if ("pending".equals(status) && !bolt11.matches("(?i)lnbc[0-9a-z]{20,8192}")) throw new IllegalArgumentException();
        }
    }
}
