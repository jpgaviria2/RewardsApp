package com.bitcoinrewards.nfcdisplay;

final class ConfirmationDecision {
    private String identity;
    private int version;
    private boolean approved;
    private boolean consumed;

    boolean begin(String identity, int version) {
        if (consumed || (this.identity != null && this.identity.equals(identity) && this.version == version && approved)) return false;
        this.identity = identity;
        this.version = version;
        this.approved = false;
        return true;
    }

    void approve() { approved = true; }
    void cancel() { approved = false; }

    boolean shouldPostConfirmation() {
        if (!approved || consumed) return false;
        consumed = true;
        return true;
    }
}
