package com.bitcoinrewards.nfcdisplay;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class BoltcardPayloadParser {
    static final int MAX_NDEF_BYTES = 4096;
    static final int MAX_NDEF_RECORDS = 8;

    private static final short TNF_WELL_KNOWN = 0x01;
    private static final byte[] URI_TYPE = new byte[] { 0x55 };
    private static final Pattern HEX_32 = Pattern.compile("[0-9A-Fa-f]{32}");
    private static final Pattern HEX_16 = Pattern.compile("[0-9A-Fa-f]{16}");
    private static final Pattern DNS_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private static final String[] URI_PREFIXES = {
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://",
        "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:",
        "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://",
        "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:",
        "urn:epc:raw:", "urn:epc:", "urn:nfc:"
    };

    private BoltcardPayloadParser() { }

    static String extractUri(short tnf, byte[] type, byte[] payload) {
        if (tnf != TNF_WELL_KNOWN || type == null || type.length != 1 || type[0] != URI_TYPE[0]) {
            throw new IllegalArgumentException("NDEF record is not a URI");
        }
        if (payload == null || payload.length < 2 || payload.length > MAX_NDEF_BYTES) {
            throw new IllegalArgumentException("Invalid NDEF URI payload length");
        }
        int prefixCode = payload[0] & 0xff;
        if (prefixCode >= URI_PREFIXES.length) throw new IllegalArgumentException("Unknown NDEF URI prefix");
        String suffix;
        try {
            suffix = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload, 1, payload.length - 1)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid NDEF URI encoding", e);
        }
        String uri = URI_PREFIXES[prefixCode] + suffix;
        if (uri.getBytes(StandardCharsets.UTF_8).length > MAX_NDEF_BYTES) {
            throw new IllegalArgumentException("NDEF URI is too long");
        }
        return uri;
    }

    static String requireBoltcard(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_NDEF_BYTES
            || value.getBytes(StandardCharsets.UTF_8).length > MAX_NDEF_BYTES
            || !value.equals(value.trim())) {
            throw new IllegalArgumentException("Invalid Boltcard URL length");
        }
        final URI uri;
        try {
            uri = new URI(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Boltcard URL", e);
        }
        if (!"lnurlw".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()
            || uri.getRawAuthority() == null || uri.getRawUserInfo() != null
            || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
            || !isDnsHost(uri.getHost())) {
            throw new IllegalArgumentException("Untrusted Boltcard URL");
        }
        String path = uri.getRawPath();
        if (path == null || path.length() <= 1 || path.length() > 2048 || path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid Boltcard provider path");
        }
        Map<String, String> query = parseStrictQuery(uri.getRawQuery());
        if (query.size() != 2 || !HEX_32.matcher(value(query, "p")).matches()
            || !HEX_16.matcher(value(query, "c")).matches()) {
            throw new IllegalArgumentException("Invalid Boltcard tap parameters");
        }
        return value;
    }

    private static Map<String, String> parseStrictQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) throw new IllegalArgumentException("Missing Boltcard tap parameters");
        Map<String, String> result = new HashMap<>();
        for (String part : rawQuery.split("&", -1)) {
            int equals = part.indexOf('=');
            if (equals <= 0 || equals != part.lastIndexOf('=')) throw new IllegalArgumentException("Invalid Boltcard query");
            String name = part.substring(0, equals);
            String value = part.substring(equals + 1);
            if (!("p".equals(name) || "c".equals(name)) || result.put(name, value) != null) {
                throw new IllegalArgumentException("Unexpected Boltcard query parameter");
            }
        }
        return result;
    }

    private static String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }

    private static boolean isDnsHost(String host) {
        if (host == null || host.isEmpty() || host.length() > 253 || host.endsWith(".")) return false;
        if (host.matches("[0-9.]+") || host.indexOf(':') >= 0) return false;
        String[] labels = host.split("\\.", -1);
        for (String label : labels) if (!DNS_LABEL.matcher(label).matches()) return false;
        return true;
    }
}
