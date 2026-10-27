package com.bitcoinrewards.nfcdisplay;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Parses the exact Bitcoin Rewards display URL used as the non-secret Store QR. */
final class StoreQrPayloadParser {
    private static final int MAX_PAYLOAD = 8192;
    private static final String PREFIX = "/plugins/bitcoin-rewards/";
    private static final String SUFFIX = "/display";

    static final class ParsedPayload {
        final String origin;
        final String storeId;

        ParsedPayload(String origin, String storeId) {
            this.origin = origin;
            this.storeId = storeId;
        }
    }

    static ParsedPayload parse(String payload) {
        if (payload == null) throw invalid();
        String value = payload.trim();
        if (value.isEmpty() || value.length() > MAX_PAYLOAD) throw invalid();
        try {
            URI uri = new URI(value);
            int port = uri.getPort() == -1 ? 443 : uri.getPort();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || port != 443 || uri.getRawQuery() != null ||
                uri.getRawFragment() != null) throw invalid();

            String path = uri.getRawPath();
            if (path == null || !path.startsWith(PREFIX) || !path.endsWith(SUFFIX)) throw invalid();
            String encodedStore = path.substring(PREFIX.length(), path.length() - SUFFIX.length());
            if (encodedStore.isEmpty() || encodedStore.indexOf('/') >= 0) throw invalid();

            String storeId = decodeCanonicalSegment(encodedStore);
            PendingOperation.validateText(storeId, 256);
            if (storeId.indexOf('/') >= 0 || storeId.indexOf('\\') >= 0 ||
                !PathSegmentCodec.encode(storeId).equalsIgnoreCase(encodedStore)) throw invalid();

            String origin = ApiOrigin.requireConfigured("https://" + uri.getHost()).toString();
            return new ParsedPayload(origin, storeId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    private static String decodeCanonicalSegment(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length();) {
            char current = value.charAt(i);
            if (current == '%') {
                if (i + 2 >= value.length()) throw invalid();
                int high = hex(value.charAt(i + 1));
                int low = hex(value.charAt(i + 2));
                if (high < 0 || low < 0) throw invalid();
                bytes.write((high << 4) | low);
                i += 3;
            } else {
                if (current > 0x7f) throw invalid();
                bytes.write((byte) current);
                i++;
            }
        }
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            throw invalid();
        }
    }

    private static int hex(char value) {
        if (value >= '0' && value <= '9') return value - '0';
        if (value >= 'a' && value <= 'f') return value - 'a' + 10;
        if (value >= 'A' && value <= 'F') return value - 'A' + 10;
        return -1;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid Store QR payload");
    }

    private StoreQrPayloadParser() { }
}
