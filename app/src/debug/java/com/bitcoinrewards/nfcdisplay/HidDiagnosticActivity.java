package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Offline physical-input harness; no network, credentials or check-in submission. */
public final class HidDiagnosticActivity extends Activity {
    private final HidScannerInput input = HidScannerInput.processInput();
    private TextView state;
    private int frames;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) frames = saved.getInt("frames", 0);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView heading = new TextView(this);
        heading.setTextSize(26);
        heading.setText("Bluetooth Scanner Test\nOFFLINE — no check-ins or rewards");
        layout.addView(heading);
        state = new TextView(this);
        state.setTextSize(20);
        layout.addView(state);
        setContentView(layout);
        showReady();
    }
    private void showReady() {
        boolean found = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && android.os.Build.VERSION.SDK_INT >= 29 &&
                HidScannerInput.acceptsDevice(id, d.getVendorId(), d.getProductId(),
                    d.isExternal(), d.isVirtual(), d.supportsSource(InputDevice.SOURCE_KEYBOARD))) found = true;
        }
        state.setText("\n" + (found ? "Scanner connected. Scan a customer QR now." : "Scanner not detected. Connect the RD scanner.") +
            "\nCompleted scans: " + frames + "\nPayloads stay on this screen only; no data is stored or sent.");
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (input.handle(event, true, this::received)) return true;
        return super.dispatchKeyEvent(event);
    }
    private void received(String raw) {
        frames++;
        String classification;
        try {
            WalletQrPayloadParser.ParsedPayload payload = WalletQrPayloadParser.parse(raw);
            classification = "VALID customer destination: " + payload.kind.name();
        } catch (IllegalArgumentException invalid) {
            classification = "Input received, but not a supported customer wallet QR.";
        }
        state.setText("\nSCAN RECEIVED\nCompleted scans: " + frames +
            "\nCharacters: " + raw.length() + "\n" + classification +
            "\n\nNo check-in or reward submitted.\nScan again to repeat.");
    }
    @Override protected void onPause() { input.reset(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("frames", frames); super.onSaveInstanceState(state); }
}
