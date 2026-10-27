package com.bitcoinrewards.nfcdisplay;

final class InlineCheckInAuthentication {
    interface CookieLookup {
        String getCookie(String url);
    }

    private InlineCheckInAuthentication() { }

    static String cookieForTrustedApi(String configuredOrigin, String apiUrl, CookieLookup lookup) {
        if (lookup == null || !ApiOrigin.sameOrigin(configuredOrigin, apiUrl)) return null;
        String cookie = lookup.getCookie(apiUrl);
        if (cookie == null || cookie.trim().isEmpty() || cookie.indexOf('\r') >= 0 || cookie.indexOf('\n') >= 0) {
            return null;
        }
        return cookie;
    }

    static boolean isLoginExpired(int responseCode) {
        return (responseCode >= 300 && responseCode < 400)
            || responseCode == 401
            || responseCode == 403;
    }

    static String failureMessage(int responseCode) {
        if (responseCode == 404) {
            return "Bitcoin Rewards check-in endpoint was not found; verify the server plugin and Store ID.";
        }
        if (responseCode == 403) {
            return "BTCPay denied check-in; the logged-in account may be missing store-settings permission.";
        }
        return "BTCPay login expired; scan login QR again";
    }
}