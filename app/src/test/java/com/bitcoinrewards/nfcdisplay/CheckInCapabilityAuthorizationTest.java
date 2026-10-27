package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CheckInCapabilityAuthorizationTest {
    @Test public void onlyExpectedValidationFailureProvesCheckInAuthorization() {
        assertTrue(CheckInCapabilityAuthorization.isAuthorizedProbeResponse(400));
        assertFalse(CheckInCapabilityAuthorization.isAuthorizedProbeResponse(200));
        assertFalse(CheckInCapabilityAuthorization.isAuthorizedProbeResponse(302));
        assertFalse(CheckInCapabilityAuthorization.isAuthorizedProbeResponse(401));
        assertFalse(CheckInCapabilityAuthorization.isAuthorizedProbeResponse(403));
        assertFalse(CheckInCapabilityAuthorization.isAuthorizedProbeResponse(404));
        assertFalse(CheckInCapabilityAuthorization.isAuthorizedProbeResponse(500));
    }

    @Test public void authenticationAndMissingPluginFailuresRemainDistinct() {
        assertTrue(CheckInCapabilityAuthorization.isAuthenticationFailure(302));
        assertTrue(CheckInCapabilityAuthorization.isAuthenticationFailure(401));
        assertTrue(CheckInCapabilityAuthorization.isAuthenticationFailure(403));
        assertFalse(CheckInCapabilityAuthorization.isAuthenticationFailure(404));
        assertFalse(CheckInCapabilityAuthorization.isAuthenticationFailure(400));
        assertFalse(CheckInCapabilityAuthorization.isAuthenticationFailure(500));
        assertTrue(CheckInCapabilityAuthorization.failureMessage(404).contains("plugin is enabled"));
    }
}
