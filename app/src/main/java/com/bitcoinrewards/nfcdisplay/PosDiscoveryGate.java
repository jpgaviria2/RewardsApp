package com.bitcoinrewards.nfcdisplay;

/** UI-thread lifecycle/profile fence. An A→B→A transition cannot revive an old attempt. */
final class PosDiscoveryGate {
    private long generation;
    private boolean active;
    private final String origin, store;
    PosDiscoveryGate(String origin, String store) { this.origin = origin; this.store = store; }
    void resume() { active = true; generation++; }
    void pause() { active = false; generation++; }
    long invalidate() { return ++generation; }
    boolean accepts(long token, String currentOrigin, String currentStore) {
        return active && token == generation && origin.equals(currentOrigin) && store.equals(currentStore);
    }
}
