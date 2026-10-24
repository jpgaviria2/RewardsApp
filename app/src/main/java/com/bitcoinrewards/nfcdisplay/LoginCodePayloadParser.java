package com.bitcoinrewards.nfcdisplay;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Parses login-code QR data without trusting QR-provided navigation targets. */
final class LoginCodePayloadParser {
    private static final int MAX_PAYLOAD = 8192;
    private static final int MAX_CODE = 4096;

    static final class ParsedPayload {
        final String origin;
        final String loginCode;
        ParsedPayload(String origin, String loginCode) {
            this.origin = origin;
            this.loginCode = loginCode;
        }
    }

    static ParsedPayload parse(String payload, String configuredOrigin) {
        if (payload == null) throw invalid();
        String value = payload.trim();
        if (value.isEmpty() || value.length() > MAX_PAYLOAD) throw invalid();

        String configured = cleanConfiguredOrigin(configuredOrigin);
        String origin;
        String code;

        if (looksLikeUrl(value)) {
            URI uri = parseHttpsUri(value);
            if (uri.getRawFragment() != null) throw invalid();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (!("/login".equalsIgnoreCase(path) || "/login/code".equalsIgnoreCase(path))) throw invalid();
            origin = origin(uri);
            code = singleQueryValue(uri.getRawQuery(), "loginCode");
        } else if (value.indexOf(';') >= 0) {
            String[] parts = value.split(";", 3);
            if (parts.length < 2) throw invalid();
            code = parts[0].trim();
            URI base = parseHttpsUri(parts[1].trim());
            if (!isOriginOnly(base)) throw invalid();
            origin = origin(base);
        } else {
            if (configured.isEmpty()) throw invalid();
            origin = configured;
            code = value;
        }

        validateCode(code);
        if (!configured.isEmpty() && !ApiOrigin.sameOrigin(configured, origin)) throw invalid();
        return new ParsedPayload(origin, code);
    }

    static String buildTrustedLoginUrl(ParsedPayload payload, String displayUrl) {
        if (payload == null || displayUrl == null || !ApiOrigin.sameOrigin(payload.origin, displayUrl)) throw invalid();
        URI display = parseHttpsUri(displayUrl);
        String returnPath = display.getRawPath();
        if (returnPath == null || returnPath.isEmpty() || display.getRawQuery() != null || display.getRawFragment() != null) throw invalid();
        return payload.origin + "/login?LoginCode=" + encode(payload.loginCode) + "&returnUrl=" + encode(returnPath);
    }

    static boolean isTrustedLoginUrl(String loginUrl, String configuredOrigin, String displayUrl) {
        try {
            if (loginUrl == null || loginUrl.length() > MAX_PAYLOAD || !ApiOrigin.sameOrigin(configuredOrigin, loginUrl)) return false;
            URI uri = parseHttpsUri(loginUrl);
            if (!"/login".equalsIgnoreCase(uri.getPath()) || uri.getRawFragment() != null) return false;
            TrustedQuery query = parseExactTrustedQuery(uri.getRawQuery());
            validateCode(query.loginCode);
            URI display = parseHttpsUri(displayUrl);
            return ApiOrigin.sameOrigin(configuredOrigin, displayUrl) && display.getRawQuery() == null && display.getRawFragment() == null
                && display.getRawPath().equals(query.returnUrl);
        } catch (Exception e) {
            return false;
        }
    }

    private static TrustedQuery parseExactTrustedQuery(String rawQuery) {
        if (rawQuery == null) throw invalid();
        String loginCode = null;
        String returnUrl = null;
        String[] fields = rawQuery.split("&", -1);
        if (fields.length != 2) throw invalid();
        String[] expectedKeys = {"LoginCode", "returnUrl"};
        String[] values = new String[2];
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            int at = field.indexOf('=');
            if (at <= 0 || at == field.length() - 1 || !expectedKeys[i].equals(field.substring(0, at))) throw invalid();
            values[i] = decode(field.substring(at + 1));
            if (values[i].isEmpty()) throw invalid();
        }
        loginCode = values[0];
        returnUrl = values[1];
        return new TrustedQuery(loginCode, returnUrl);
    }

    private static String cleanConfiguredOrigin(String configuredOrigin) {
        if (configuredOrigin == null || configuredOrigin.trim().isEmpty()) return "";
        return ApiOrigin.requireConfigured(configuredOrigin.trim()).toString();
    }

    private static URI parseHttpsUri(String value) {
        try {
            URI uri = new URI(value);
            int port = uri.getPort() == -1 ? 443 : uri.getPort();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || port != 443) throw invalid();
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    private static boolean isOriginOnly(URI uri) {
        String path = uri.getPath();
        return (path == null || path.isEmpty() || "/".equals(path)) && uri.getRawQuery() == null && uri.getRawFragment() == null;
    }

    private static String origin(URI uri) {
        return ApiOrigin.requireConfigured("https://" + uri.getHost()).toString();
    }

    private static String singleQueryValue(String rawQuery, String wanted) {
        if (rawQuery == null) throw invalid();
        List<String> values = new ArrayList<>();
        for (String item : rawQuery.split("&", -1)) {
            int at = item.indexOf('=');
            String key = decode(at < 0 ? item : item.substring(0, at));
            if (wanted.equalsIgnoreCase(key)) values.add(decode(at < 0 ? "" : item.substring(at + 1)));
        }
        if (values.size() != 1) throw invalid();
        return values.get(0);
    }

    private static void validateCode(String code) {
        if (code == null || code.isEmpty() || code.length() > MAX_CODE || code.indexOf('\r') >= 0 || code.indexOf('\n') >= 0) throw invalid();
    }

    private static boolean looksLikeUrl(String value) {
        return value.regionMatches(true, 0, "https://", 0, 8) || value.regionMatches(true, 0, "http://", 0, 7);
    }

    private static String decode(String value) {
        try {
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == '%' && (i + 2 >= value.length() || !isHex(value.charAt(i + 1)) || !isHex(value.charAt(i + 2)))) throw invalid();
            }
            String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
            if (decoded.indexOf('\uFFFD') >= 0) throw invalid();
            return decoded;
        }
        catch (Exception e) { throw invalid(); }
    }

    private static boolean isHex(char value) {
        return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f') || (value >= 'A' && value <= 'F');
    }

    private static String encode(String value) {
        try { return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20"); }
        catch (Exception e) { throw invalid(); }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid BTCPay login code payload");
    }

    private static final class TrustedQuery {
        final String loginCode;
        final String returnUrl;

        TrustedQuery(String loginCode, String returnUrl) {
            this.loginCode = loginCode;
            this.returnUrl = returnUrl;
        }
    }

    private LoginCodePayloadParser() { }
}
