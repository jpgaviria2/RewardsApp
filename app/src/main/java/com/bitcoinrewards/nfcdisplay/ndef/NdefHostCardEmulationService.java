package com.bitcoinrewards.nfcdisplay.ndef;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

/**
 * HCE service for broadcasting LNURL-withdraw via NFC NDEF Type 4 Tag emulation.
 * Adapted from Numo — stripped to read-only broadcast (no Cashu, no write-back).
 */
public class NdefHostCardEmulationService extends HostApduService {
    private static final String TAG = "NdefHCEService";
    private static final byte[] STATUS_FAILED = {(byte) 0x6F, (byte) 0x00};

    private NdefProcessor ndefProcessor;
    private static NdefHostCardEmulationService instance;
    private boolean tapNotified = false;

    public interface NfcTapListener {
        void onNfcTapDetected();
    }

    private NfcTapListener tapListener;

    public static NdefHostCardEmulationService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ndefProcessor = new NdefProcessor();
        instance = this;
        Log.i(TAG, "HCE Service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        Log.i(TAG, "HCE Service destroyed");
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        try {
            if (!tapNotified && tapListener != null) {
                tapNotified = true;
                tapListener.onNfcTapDetected();
            }

            byte[] response = ndefProcessor.processCommandApdu(commandApdu);
            if (response != NdefConstants.NDEF_RESPONSE_ERROR) {
                return response;
            }
            return STATUS_FAILED;
        } catch (Exception e) {
            Log.e(TAG, "Error processing APDU: " + e.getMessage(), e);
            return STATUS_FAILED;
        }
    }

    @Override
    public void onDeactivated(int reason) {
        tapNotified = false;
        Log.i(TAG, "HCE deactivated, reason: " + reason);
    }

    public void setPaymentRequest(String uri) {
        if (ndefProcessor != null) {
            ndefProcessor.setMessageToSend(uri);
            Log.i(TAG, "Broadcasting: " + uri.substring(0, Math.min(40, uri.length())) + "...");
        }
    }

    public void clearPaymentRequest() {
        if (ndefProcessor != null) {
            ndefProcessor.setMessageToSend("");
            Log.i(TAG, "Broadcast cleared");
        }
    }

    public void setTapListener(NfcTapListener listener) {
        this.tapListener = listener;
    }

    public static boolean isHceAvailable(Context context) {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
            return adapter != null && adapter.isEnabled() &&
                    context.getPackageManager().hasSystemFeature(
                            PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
