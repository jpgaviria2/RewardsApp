package com.bitcoinrewards.nfcdisplay;

import java.util.Locale;

/** Ephemeral presentation state; never a check-in or payment authority. */
final class CheckInCelebration {
    private String lastConfirmedId;
    private long requestEpoch;
    private boolean armed;
    private boolean celebrating;
    private String name = "";

    static boolean isConfirmedHttpStatus(int status) { return status == 200 || status == 201; }

    void begin(long epoch) {
        clear();
        requestEpoch = epoch;
        armed = true;
    }

    boolean confirm(String id, String serverName, long epoch) {
        if (!armed || epoch != requestEpoch || !CollectionCheckInStore.valid(id)) return false;
        String key = id.toLowerCase(Locale.ROOT);
        // One terminal response per operation, plus the current confirmation identity.
        // MainActivity admits only its owned task once; the pinned creation API returns
        // a new GUID or rejects active admission (503), never a historical success ID.
        // This is not a historical replay ledger or financial authorization.
        armed = false;
        if (key.equals(lastConfirmedId)) return false;
        lastConfirmedId = key;
        celebrating = true;
        name = "";
        if (serverName != null && serverName.length() <= 160 && !serverName.trim().isEmpty()) {
            boolean safe = true;
            for (int i = 0; i < serverName.length(); i++) {
                char c = serverName.charAt(i);
                if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) safe = false;
            }
            if (safe) name = serverName;
        }
        return true;
    }

    void clear() {
        armed = false;
        celebrating = false;
        name = "";
    }

    String name() { return name; }
    boolean isCelebrating() { return celebrating; }
}
