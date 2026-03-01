package com.bitcoinrewards.nfcdisplay.ndef;

import java.util.Arrays;

/**
 * Routes APDU commands for read-only NDEF Type 4 Tag emulation.
 * No UPDATE BINARY support — this is a read-only tag.
 */
public class NdefProcessor {
    private final NdefStateManager stateManager;
    private final NdefApduHandler apduHandler;

    public NdefProcessor() {
        this.stateManager = new NdefStateManager();
        this.apduHandler = new NdefApduHandler(stateManager);
    }

    public void setMessageToSend(String message) {
        stateManager.setMessageToSend(message);
    }

    public byte[] processCommandApdu(byte[] commandApdu) {
        // SELECT AID
        if (Arrays.equals(commandApdu, NdefConstants.NDEF_SELECT_AID)) {
            return NdefConstants.NDEF_RESPONSE_OK;
        }

        // SELECT FILE
        if (commandApdu.length >= 7 &&
            Arrays.equals(Arrays.copyOfRange(commandApdu, 0, 4), NdefConstants.NDEF_SELECT_FILE_HEADER)) {
            return apduHandler.handleSelectFile(commandApdu);
        }

        // READ BINARY
        if (commandApdu.length >= 5 &&
            Arrays.equals(Arrays.copyOfRange(commandApdu, 0, 2), NdefConstants.NDEF_READ_BINARY_HEADER)) {
            return apduHandler.handleReadBinary(commandApdu);
        }

        return NdefConstants.NDEF_RESPONSE_ERROR;
    }
}
