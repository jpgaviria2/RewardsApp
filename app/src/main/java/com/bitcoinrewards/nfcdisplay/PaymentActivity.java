package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.nfc.NfcAdapter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.graphics.Bitmap;
import android.view.WindowManager;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Separate foreground collection mode: never calls a rewards endpoint or a card issuer. */
public final class PaymentActivity extends Activity {
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PaymentOperationStore operations;
    private CollectionCheckInStore checkIns;
    private PaymentApi api;
    private LinearLayout layout;
    private TextView message;

    private PaymentContract.Invoice invoice;
    private boolean busy, resumed, destroyed;
    private NfcAdapter adapter;
    private final PaymentTapGate tapGate = new PaymentTapGate();
    private final Runnable poll = () -> { if (resumed && operations.exists() && !busy) reconcile(); };
    interface Work { JSONObject run() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
        com.bitcoinrewards.nfcdisplay.ndef.NdefHostCardEmulationService.clearPayload();
        operations = new PaymentOperationStore(this);
        checkIns = new CollectionCheckInStore(this);
        try {
            api = new PaymentApi(operations.exists() ? operations.origin() : SettingsActivity.getBtcpayUrl(this),
                operations.exists() ? operations.store() : SettingsActivity.getStoreId(this));
        } catch (Exception e) { finish(); return; }
        adapter = NfcAdapter.getDefaultAdapter(this);
        if (operations.exists()) reconcile(); else history();
    }
    private void screen(String text) {
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 16, 20, 16);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
        message = new TextView(this);
        message.setTextSize(22);
        message.setText(text);
        layout.addView(message);
    }
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(v -> action.run());
        layout.addView(button);
        return button;
    }
    private void probe() {
        history();
    }

    private void history() {
        try {
            org.json.JSONArray items = operations.history();
            screen("Payment history — recorded statuses, not live settlement. Select a reference to read and reconcile its current server status. No replacement invoice is created.");
            for (int i = items.length() - 1; i >= 0; i--) {
                final int index = i;
                JSONObject item = items.getJSONObject(i);
                button(item.getString("status") + " — " + PaymentContract.money(item.getLong("amount")) + "\n" + item.getString("operation"), () -> {
                    if (!busy && operations.recoverHistory(index)) {
                        api = new PaymentApi(operations.origin(), operations.store());
                        invoice = null;
                        reconcile();
                    }
                });
            }
            button("Back to rewards", this::finish);
        } catch (Exception e) { screen("Payment history unavailable. Ask staff to reconcile in BTCPay; do not replace an uncertain charge."); }
    }

    private void reconcile() {
        if (busy || !operations.exists()) return;
        if (invoice == null) screen("Recovering payment status… Do not charge again.");
        request(() -> api.request("GET", "/invoices/" + operations.id(), null), true, null);
    }
    interface Success { void accept(JSONObject json) throws Exception; }
    private void request(Work work, boolean invoiceResponse, Success success) {
        if (busy) return;
        busy = true;
        handler.removeCallbacks(poll);
        NETWORK.execute(() -> {
            JSONObject result = null;
            Exception failure = null;
            try { result = work.run(); } catch (Exception e) { failure = e; }
            final JSONObject response = result;
            final Exception error = failure;
            handler.post(() -> {
                if (destroyed) return;
                busy = false;
                try {
                    if (error != null) throw error;
                    if (invoiceResponse) {
                        PaymentContract.Invoice next = new PaymentContract.Invoice(response, operations.id(), operations.amount(), operations.invoiceId());
                        if (next.invoiceId != null && !operations.bindInvoice(next.invoiceId)) throw new IllegalArgumentException();
                        if (invoice != null && !invoice.bolt11.isEmpty() && !next.bolt11.isEmpty() && !invoice.bolt11.equals(next.bolt11)) throw new IllegalArgumentException();
                        invoice = next;
                        if ((next.invoiceId != null || PaymentContract.terminal(next.status))
                            && !checkIns.release(operations.origin(), operations.store(), operations.expectedCheckInId())) throw new IllegalStateException();
                        showInvoice();
                    } else if (success != null) success.accept(response);
                } catch (Exception e) {
                    // Drop QR and NFC eligibility on any ambiguous or unauthenticated result.
                    invoice = null;
                    screen(e instanceof PaymentApi.Failure ? ((PaymentApi.Failure)e).guidance()
                        : "Payment outcome unknown. Refresh status; do not charge again.");
                    if (operations.exists()) {
                        button("Refresh status", this::reconcile);
                        button("Reconcile with server", this::requestReconciliation);
                        button("Request cancellation", this::cancel);
                        button("Settings / login", () -> startActivity(new Intent(this, SettingsActivity.class)));
                    } else { button("Check availability again", this::probe); button("Payment history / late settlement", this::history); button("Return to check-in", this::finish); }
                }
                schedulePoll();
            });
        });
    }
    private void showInvoice() {
        screen(PaymentContract.money(invoice.amountMinor) + "\n" + invoice.status.toUpperCase(java.util.Locale.ROOT)
            + "\nNo rewards for this sale\nExpires: " + invoice.expiresAt);
        if ("pending".equals(invoice.status)) {
            try {
                BitMatrix matrix = new MultiFormatWriter().encode(invoice.bolt11, BarcodeFormat.QR_CODE, 512, 512);
                int[] pixels = new int[512*512];
                for (int y=0; y<512; y++) for (int x=0; x<512; x++) pixels[y*512+x] = matrix.get(x,y) ? 0xff000000 : 0xffffffff;
                ImageView qr = new ImageView(this);
                qr.setImageBitmap(Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888));
                qr.setContentDescription("Lightning invoice QR");
                layout.addView(qr, new LinearLayout.LayoutParams(-1, dp(280)));
                TextView hint = new TextView(this);
                hint.setText(operations.tapAttempted() ? "Card attempt already sent. Awaiting server settlement; do not tap again." : "Scan QR with a Lightning wallet or tap your Boltcard.");
                layout.addView(hint);
            } catch (Exception e) { message.append("\nQR unavailable. Refresh status."); }
        }
        if (PaymentContract.terminal(invoice.status)) {
            button("Reconcile with server", this::requestReconciliation);
            button("paid".equals(invoice.status) ? "Payment received — finish" : "Finish and return to check-in", () -> {
                if (!busy && invoice != null && operations.clearTerminal(invoice.status)) finish();
            });
        } else {
            if ("review".equals(invoice.status)) message.append("\nStaff review required. Do not create another charge.");
            button("Refresh status", this::reconcile);
            button("Reconcile with server", this::requestReconciliation);
            button("Request cancellation", this::cancel);
        }
    }
    private void cancel() {
        if (busy || !operations.exists() || (invoice != null && PaymentContract.terminal(invoice.status))) return;
        // Always read back after cancellation: settlement can win this race.
        request(() -> api.cancelAndRead(operations.id()), true, null);
    }
    private void requestReconciliation() {
        if (busy || !operations.exists()) return;
        EditText reason = new EditText(this);
        reason.setHint("Operator reason (required, maximum 500 characters)");
        reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        new AlertDialog.Builder(this).setTitle("Reconcile with server?")
            .setMessage("Authorized staff only. The server may revoke this invoice if it can prove non-payability; uncertainty remains locked. Do not enter customer or card secrets.")
            .setView(reason).setNegativeButton("Back", null)
            .setPositiveButton("Reconcile", (dialog, which) -> {
                String text = reason.getText().toString().trim();
                if (text.isEmpty()) { Toast.makeText(this, "A reconciliation reason is required.", Toast.LENGTH_LONG).show(); return; }
                request(() -> api.reconcileAndRead(operations, text), true, null);
            }).show();
    }
    private void schedulePoll() {
        handler.removeCallbacks(poll);
        if (resumed && operations.exists() && invoice != null && !PaymentContract.terminal(invoice.status)) handler.postDelayed(poll, 3000);
    }
    private void tag(Tag tag) {
        long ticket = tapGate.ticket();
        if (invoice == null || !tapGate.allows(ticket, busy, invoice.status, operations.tapAttempted())) return;
        NETWORK.execute(() -> {
            String card = null;
            try {
                Ndef ndef = Ndef.get(tag);
                if (ndef == null) return;
                try {
                    ndef.connect();
                    NdefMessage message = ndef.getNdefMessage();
                    if (message == null || message.toByteArray().length > BoltcardPayloadParser.MAX_NDEF_BYTES || message.getRecords().length != 1) return;
                    NdefRecord r = message.getRecords()[0];
                    card = BoltcardPayloadParser.requireBoltcard(BoltcardPayloadParser.extractUri(r.getTnf(), r.getType(), r.getPayload()));
                } finally { ndef.close(); }
            } catch (Exception ignored) { }
            final String payload = card;
            handler.post(() -> {
                if (!destroyed && invoice != null && tapGate.allows(ticket, busy, invoice.status, operations.tapAttempted())
                    && payload != null && operations.markTap()) {
                    request(() -> api.tapAndRead(operations.id(), payload), true, null);
                }
            });
        });
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true; tapGate.resume();
        // Consume every foreground NFC tap, including duplicates/review; submit only through tag's gate.
        if (adapter != null) try { adapter.enableReaderMode(this, this::tag,
            NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null); } catch (IllegalStateException ignored) { }
        if (operations != null && operations.exists() && !busy) reconcile();
    }
    @Override protected void onPause() {
        resumed = false; tapGate.pause(); handler.removeCallbacks(poll);
        if (adapter != null) try { adapter.disableReaderMode(this); } catch (IllegalStateException ignored) { }
        super.onPause();
    }
    @Override protected void onDestroy() { destroyed = true; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void onBackPressed() {
        if (operations != null && operations.exists()) { Toast.makeText(this, "Finish or reconcile this payment before returning to rewards.", Toast.LENGTH_LONG).show(); }
        else super.onBackPressed();
    }
}
