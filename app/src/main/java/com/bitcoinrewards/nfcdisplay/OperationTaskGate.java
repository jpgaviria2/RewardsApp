package com.bitcoinrewards.nfcdisplay;

import java.util.concurrent.Future;

/** Single-worker ownership shared by the production scan coordinator and its tests. */
final class OperationTaskGate {
    interface Disconnectable { void disconnect(); }
    private Future<?> worker;
    private Disconnectable connection;

    synchronized boolean claim(Future<?> candidate) {
        if (candidate == null || running()) return false;
        worker = candidate;
        return true;
    }

    synchronized boolean running() { return worker != null && !worker.isDone(); }

    synchronized void completed(Future<?> candidate) {
        if (worker == candidate) { worker = null; connection = null; }
    }

    synchronized void connection(Disconnectable value) {
        if (running()) connection = value;
        else if (value != null) value.disconnect();
    }

    synchronized void clearConnection(Disconnectable value) {
        if (connection == value) connection = null;
    }

    /** UI detachment is deliberately a no-op: recreation must not restart the operation. */
    void uiDetached() { }

    synchronized void shutdown() {
        if (connection != null) connection.disconnect();
        connection = null;
        if (worker != null) worker.cancel(true);
        worker = null;
    }
}
