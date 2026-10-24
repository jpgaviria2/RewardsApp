package com.bitcoinrewards.nfcdisplay;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

final class WalletQrPayloadParser {
    enum Kind { LIGHTNING_ADDRESS, LNURL }

    static final class ParsedPayload {
        final Kind kind;
        final String value;

        ParsedPayload(Kind kind, String value) {
            this.kind = kind;
            this.value = value;
        }

        String displayLabel() {
            if (kind == Kind.LNURL) return "LNURL-pay code";
            int at = value.indexOf('@');
            if (at <= 1) return "Lightning address";
            String local = value.substring(0, at);
            String domain = value.substring(at + 1);
            return local.substring(0, Math.min(3, local.length())) + "…@" + domain;
        }
    }

    private static final Pattern LIGHTNING_ADDRESS = Pattern.compile(
        "^[A-Za-z0-9](?:[A-Za-z0-9._-]{0,62}[A-Za-z0-9])?@" +
        "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    );
    private static final String BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";

    private WalletQrPayloadParser() { }

    static ParsedPayload parse(String raw) {
        if (raw == null) {
            throw invalidPayload();
        }
        try {
            int byteCount = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(raw))
                .remaining();
            if (byteCount > 4096) throw invalidPayload();
        } catch (CharacterCodingException e) {
            throw invalidPayload();
        }

        int start = 0;
        int end = raw.length();
        while (start < end && isOuterWhitespace(raw.charAt(start))) start++;
        while (end > start && isOuterWhitespace(raw.charAt(end - 1))) end--;
        String value = raw.substring(start, end);
        if (value.isEmpty()) throw invalidPayload();
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) throw invalidPayload();
        }

        if (value.regionMatches(true, 0, "lightning:", 0, "lightning:".length())) {
            value = value.substring("lightning:".length());
            if (value.isEmpty() || value.regionMatches(true, 0, "lightning:", 0, "lightning:".length())) {
                throw invalidPayload();
            }
        }

        if (isValidLnurlBech32(value)) {
            return new ParsedPayload(Kind.LNURL, value);
        }
        if (value.length() <= 128 && LIGHTNING_ADDRESS.matcher(value).matches()) {
            return new ParsedPayload(Kind.LIGHTNING_ADDRESS, value);
        }
        throw invalidPayload();
    }

    private static boolean isOuterWhitespace(char value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static IllegalArgumentException invalidPayload() {
        return new IllegalArgumentException("QR must contain a Lightning address or LNURL-pay code.");
    }

    private static boolean isValidLnurlBech32(String value) {
        if (value.length() < 8) return false;
        boolean hasLower = false;
        boolean hasUpper = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 33 || c > 126) return false;
            if (c >= 'a' && c <= 'z') hasLower = true;
            if (c >= 'A' && c <= 'Z') hasUpper = true;
        }
        if (hasLower && hasUpper) return false;

        String normalized = value.toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('1');
        if (separator != 5 || !"lnurl".equals(normalized.substring(0, separator))) return false;
        if (normalized.length() - separator - 1 < 6) return false;

        int[] values = new int[normalized.length() - separator - 1];
        for (int i = 0; i < values.length; i++) {
            int index = BECH32_CHARSET.indexOf(normalized.charAt(separator + 1 + i));
            if (index < 0) return false;
            values[i] = index;
        }
        if (!hasValidBech32Checksum(normalized.substring(0, separator), values)) return false;

        int accumulator = 0;
        int bits = 0;
        for (int i = 0; i < values.length - 6; i++) {
            accumulator = (accumulator << 5) | values[i];
            bits += 5;
            while (bits >= 8) {
                bits -= 8;
                accumulator &= (1 << bits) - 1;
            }
        }
        return bits < 5 && ((accumulator << (8 - bits)) & 0xff) == 0;
    }

    private static boolean hasValidBech32Checksum(String hrp, int[] data) {
        int polymod = 1;
        for (int i = 0; i < hrp.length(); i++) {
            polymod = bech32PolymodStep(polymod) ^ (hrp.charAt(i) >> 5);
        }
        polymod = bech32PolymodStep(polymod);
        for (int i = 0; i < hrp.length(); i++) {
            polymod = bech32PolymodStep(polymod) ^ (hrp.charAt(i) & 31);
        }
        for (int value : data) {
            polymod = bech32PolymodStep(polymod) ^ value;
        }
        return polymod == 1;
    }

    private static int bech32PolymodStep(int pre) {
        int bottom = pre >>> 25;
        int checksum = (pre & 0x1ffffff) << 5;
        if ((bottom & 1) != 0) checksum ^= 0x3b6a57b2;
        if ((bottom & 2) != 0) checksum ^= 0x26508e6d;
        if ((bottom & 4) != 0) checksum ^= 0x1ea119fa;
        if ((bottom & 8) != 0) checksum ^= 0x3d4233dd;
        if ((bottom & 16) != 0) checksum ^= 0x2a1462b3;
        return checksum;
    }
}
