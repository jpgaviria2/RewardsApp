package com.bitcoinrewards.nfcdisplay;

final class CheckInCapabilityAuthorization {
    private CheckInCapabilityAuthorization() { }

    // The protected check-in endpoint validates authorization before its body.
    // An intentionally empty destination therefore returns 400 only after the
    // current BTCPay cookie has passed the same policy used by real check-ins.
    static boolean isAuthorizedProbeResponse(int responseCode) {
        return responseCode == 400;
    }

    static boolean isAuthenticationFailure(int responseCode) {
        return (responseCode >= 300 && responseCode < 400)
            || responseCode == 401
            || responseCode == 403;
    }

    static String failureMessage(int responseCode) {
        if (responseCode == 404) {
            return "Bitcoin Rewards is unavailable on this BTCPay server. Verify that the plugin is enabled and the Store ID is correct.";
        }
        if (isAuthenticationFailure(responseCode)) {
            return "BTCPay login does not have rewards check-in access. Open Settings and scan a fresh login QR from an account that can modify this store.";
        }
        return "Could not verify BTCPay rewards check-in access. Check the server connection and try the login QR again.";
    }
}
