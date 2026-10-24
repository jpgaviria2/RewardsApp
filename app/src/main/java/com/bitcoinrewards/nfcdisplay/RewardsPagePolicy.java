package com.bitcoinrewards.nfcdisplay;

import java.net.URI;

final class RewardsPagePolicy {
    private final String origin;
    private final String rewardsBasePath;

    RewardsPagePolicy(String origin, String storeId) {
        this.origin = ApiOrigin.requireConfigured(origin).toString();
        PendingOperation.validateText(storeId, 256);
        if (storeId == null || storeId.isEmpty()) throw new IllegalArgumentException();
        rewardsBasePath = "/plugins/bitcoin-rewards/" + PathSegmentCodec.encode(storeId);
    }

    boolean isEligiblePageCompletion(String completedUrl, String currentMainFrameUrl) {
        return completedUrl != null
            && completedUrl.equals(currentMainFrameUrl)
            && isTrustedRewardsPage(completedUrl);
    }

    boolean isTrustedRewardsPage(String url) {
        if (!ApiOrigin.sameOrigin(origin, url)) return false;
        try {
            String path = new URI(url).getRawPath();
            return path.equals(rewardsBasePath + "/display")
                || path.equals(rewardsBasePath + "/display/")
                || path.equals(rewardsBasePath + "/check-in")
                || path.startsWith(rewardsBasePath + "/check-in/");
        } catch (Exception e) {
            return false;
        }
    }
}
