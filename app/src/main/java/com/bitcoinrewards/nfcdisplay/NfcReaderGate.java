package com.bitcoinrewards.nfcdisplay;

final class NfcReaderGate {
    private boolean resumed;
    private boolean eligiblePage;
    private boolean recovering;
    private boolean authenticationFailure;
    private boolean submissionInFlight;

    void update(boolean resumed, boolean eligiblePage, boolean recovering,
                boolean authenticationFailure, boolean submissionInFlight) {
        this.resumed = resumed;
        this.eligiblePage = eligiblePage;
        this.recovering = recovering;
        this.authenticationFailure = authenticationFailure;
        this.submissionInFlight = submissionInFlight;
    }

    boolean shouldEnable() {
        return resumed && eligiblePage && !recovering && !authenticationFailure && !submissionInFlight;
    }
}
