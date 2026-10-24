package com.bitcoinrewards.nfcdisplay;

final class MainFrameLoadGate {
    private String activeMainFrameUrl;
    private boolean mainFrameLoadFailed = true;

    void onPageStarted(String url) {
        activeMainFrameUrl = url;
        mainFrameLoadFailed = false;
    }

    void onMainFrameLoadFailed() {
        mainFrameLoadFailed = true;
    }

    boolean isSuccessfulCompletion(String completedUrl) {
        return !mainFrameLoadFailed
            && completedUrl != null
            && completedUrl.equals(activeMainFrameUrl);
    }

    boolean isLikelyLegacyMainFrameError(String failingUrl, String currentMainFrameUrl) {
        return failingUrl != null
            && (failingUrl.equals(activeMainFrameUrl) || failingUrl.equals(currentMainFrameUrl));
    }

    static boolean isHttpFailure(int statusCode) {
        return statusCode >= 400 && statusCode <= 599;
    }
}
