package com.bitcoinrewards.nfcdisplay;

final class DisplayRecoveryUiState {
    private enum Mode { NORMAL, RETRYING, TERMINAL_FAILURE }

    private static final String RETRYING_MESSAGE = "Reconnecting to BTCPay...";
    private static final String TERMINAL_MESSAGE =
        "Could not reconnect to BTCPay. Check the server and Wi-Fi, then reopen the app or use Settings.";

    private Mode mode = Mode.NORMAL;

    void onTrustedDisplayLoadStarted() {
        mode = Mode.NORMAL;
    }

    void onTransientFailure(boolean retryActive) {
        mode = retryActive ? Mode.RETRYING : Mode.TERMINAL_FAILURE;
    }

    void onSuccessfulTrustedDisplay() {
        mode = Mode.NORMAL;
    }

    boolean shouldHoldFailureUi() {
        return mode != Mode.NORMAL;
    }

    String message() {
        return mode == Mode.RETRYING ? RETRYING_MESSAGE : TERMINAL_MESSAGE;
    }
}
