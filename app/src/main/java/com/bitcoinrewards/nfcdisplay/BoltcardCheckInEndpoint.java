package com.bitcoinrewards.nfcdisplay;

import java.net.URI;

final class BoltcardCheckInEndpoint {
    private BoltcardCheckInEndpoint() { }

    static String fromLegacyCheckInUrl(String legacyUrl) {
        try {
            URI legacy = new URI(legacyUrl);
            String path = legacy.getRawPath();
            if (!"https".equalsIgnoreCase(legacy.getScheme()) || legacy.getHost() == null
                || legacy.getRawUserInfo() != null || legacy.getRawQuery() != null
                || legacy.getRawFragment() != null || path == null || !path.endsWith("/check-in/api")) {
                throw new IllegalArgumentException("Invalid legacy check-in URL");
            }
            return legacy.toASCIIString() + "/boltcard";
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid legacy check-in URL", e);
        }
    }
}
