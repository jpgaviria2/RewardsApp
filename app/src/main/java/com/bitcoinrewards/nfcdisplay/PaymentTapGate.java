package com.bitcoinrewards.nfcdisplay;

/** Invalidates NFC reads across pause/resume; interception and charge dispatch are distinct. */
final class PaymentTapGate {
    private volatile long generation;
    private volatile boolean resumed;
    void resume() { generation++; resumed = true; }
    void pause() { resumed = false; generation++; }
    long ticket() { return generation; }
    boolean allows(long ticket, boolean busy, String status, boolean attempted) {
        return resumed && ticket == generation && !busy && "pending".equals(status) && !attempted;
    }
}
