package com.bitcoinrewards.nfcdisplay;

import android.os.Build;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;

/** Only the verified RD HID scanner, never a general keyboard or injected text. */
final class HidScannerInput {
    // Process-owned framing only. Never retains an Activity, Context or listener.
    // All foreground surfaces must observe the same blocked/partial frame.
    private static final HidScannerInput PROCESS_INPUT = new HidScannerInput();
    static HidScannerInput processInput() { return PROCESS_INPUT; }
    interface PayloadListener { void accept(String raw); }
    private final HidScanFramer framer = new HidScanFramer();
    private String sourceId;

    static boolean acceptsDevice(int id, int vendor, int product, boolean external,
                                 boolean virtual, boolean keyboard) {
        return id > 0 && vendor == 0x045e && product == 0x0040
            && external && !virtual && keyboard;
    }

    void reset() { framer.reset(); }

    boolean handle(KeyEvent event, boolean enabled, PayloadListener listener) {
        // Identification uses public API19 getters; consuming does not authorize submission.
        InputDevice device = event.getDevice();
        if (device == null || !acceptsDevice(event.getDeviceId(), device.getVendorId(),
                device.getProductId(), true, device.isVirtual(),
                event.isFromSource(InputDevice.SOURCE_KEYBOARD))) return false;
        String source = device.getDescriptor() + ":" + device.getId();
        boolean delimiter = event.getKeyCode() == KeyEvent.KEYCODE_ENTER
            || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER;
        // External attestation is public only on API29+. Track rejected frames without
        // decoding text: even an empty buffer can have a blocked scanner prefix.
        if (!enabled || Build.VERSION.SDK_INT < 29 || !device.isExternal()) {
            if (delimiter && event.getAction() == KeyEvent.ACTION_UP) {
                framer.reset();
            } else {
                framer.accept(source, 0, delimiter && event.getAction() == KeyEvent.ACTION_DOWN,
                    false, event.getEventTime(), false);
            }
            return true;
        }
        if (sourceId != null && !sourceId.equals(source)) framer.reset();
        sourceId = source;
        // Consume both halves before a focused WebView/button can see scanner Enter.
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (!enabled || event.isCanceled()) framer.reset();
            return true;
        }
        boolean command = event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed();
        if ((event.getKeyCode() == KeyEvent.KEYCODE_SHIFT_LEFT
                || event.getKeyCode() == KeyEvent.KEYCODE_SHIFT_RIGHT) && !command) {
            if (!enabled || event.isCanceled()) framer.reset();
            return true;
        }
        int unicode = event.getUnicodeChar(event.getMetaState());
        boolean valid = enabled && !command && event.getAction() == KeyEvent.ACTION_DOWN
            && !event.isCanceled() && event.getEventTime() <= SystemClock.uptimeMillis()
            && SystemClock.uptimeMillis() - event.getEventTime() <= 500;
        String raw = framer.accept(device.getDescriptor() + ":" + device.getId(), unicode,
            delimiter, event.getRepeatCount() != 0, event.getEventTime(), valid);
        if (raw != null) listener.accept(raw);
        return true;
    }
}
