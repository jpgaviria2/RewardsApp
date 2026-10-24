package com.bitcoinrewards.nfcdisplay;

import java.net.URI;
import java.util.Locale;

final class ApiOrigin {
    final String host;
    private ApiOrigin(String host) { this.host = host; }

    static ApiOrigin requireConfigured(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null ||
                uri.getFragment() != null || uri.getQuery() != null || (uri.getPort() != -1 && uri.getPort() != 443) ||
                !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw new IllegalArgumentException();
            return new ApiOrigin(uri.getHost().toLowerCase(Locale.US));
        } catch (Exception e) { throw new IllegalArgumentException("Invalid configured server origin"); }
    }

    static boolean sameOrigin(String configured, String candidate) {
        try {
            ApiOrigin expected = requireConfigured(configured);
            URI uri = new URI(candidate);
            int port = uri.getPort() == -1 ? 443 : uri.getPort();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getHost() != null &&
                port == 443 && expected.host.equals(uri.getHost().toLowerCase(Locale.US));
        } catch (Exception e) { return false; }
    }

    @Override public String toString() { return "https://" + host; }
}
