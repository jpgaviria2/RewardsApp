package com.bitcoinrewards.nfcdisplay;

/** Delimiter-framed input only; does not parse destinations or perform I/O. */
final class HidScanFramer {
    private final StringBuilder buffer = new StringBuilder();
    private boolean discarding;
    private long firstAt, lastAt;
    private String sourceId;

    void reset() {
        if (buffer.length() > 0) discarding = true;
        buffer.setLength(0);
    }

    String accept(String source, int codePoint, boolean delimiter, boolean repeat, long now, boolean enabled) {
        if (sourceId != null && !sourceId.equals(source)) { buffer.setLength(0); discarding = true; }
        sourceId = source;
        boolean stale = buffer.length() > 0 && (now < lastAt || now - lastAt > 500 || now - firstAt > 10_000);
        if (stale) { buffer.setLength(0); discarding = true; }
        if (buffer.length() >= 4096 && !delimiter) { buffer.setLength(0); discarding = true; }
        if (!enabled || repeat || (!delimiter && (codePoint < 32 || codePoint > 126))) { buffer.setLength(0); discarding = true; }
        if (delimiter) {
            String value = discarding || buffer.length() == 0 ? null : buffer.toString();
            discarding = false;
            buffer.setLength(0);
            return value;
        }
        if (!discarding) {
            if (buffer.length() == 0) firstAt = now;
            lastAt = now;
            buffer.appendCodePoint(codePoint);
        }
        return null;
    }
}
