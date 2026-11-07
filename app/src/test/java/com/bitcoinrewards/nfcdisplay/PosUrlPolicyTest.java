package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class PosUrlPolicyTest {
    @Test public void canonicalSameServerPosOnly() {
        assertEquals("https://merchant.example/apps/Ab123/pos", PosUrlPolicy.require("https://merchant.example", "https://merchant.example/apps/Ab123/pos"));
        assertEquals("https://merchant.example/apps/Ab123/pos", PosUrlPolicy.require("https://merchant.example", "https://MERCHANT.example:443/apps/Ab123/pos"));
    }
    @Test public void rejectsUntrustedOrSecretBearingEntryPoints() {
        for (String value : new String[]{"http://merchant.example/apps/A/pos", "https://other.example/apps/A/pos", "https://merchant.example:444/apps/A/pos", "https://u:p@merchant.example/apps/A/pos", "https://merchant.example/apps/A/pos?code=secret", "https://merchant.example/apps/A/pos#fragment", "https://merchant.example/Account/Login", "https://merchant.example/apps/A%2fB/pos", "https://merchant.example/apps/A/../pos", "javascript:alert(1)", "file:///apps/A/pos", "//merchant.example/apps/A/pos", "https://merchant.example/apps//pos", "https://merchant.example/apps/A/pos/", ""}) {
            try { PosUrlPolicy.require("https://merchant.example", value); fail(value); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void navigationRequiresTrustedHttpsOrigin() {
        WebOriginPolicy policy = new WebOriginPolicy("https://merchant.example");
        assertTrue(policy.allowNavigation("https://merchant.example/i/invoice"));
        assertFalse(policy.allowNavigation("https://merchant.example.evil/i"));
        assertFalse(policy.allowNavigation("intent://merchant.example/i"));
        assertFalse(policy.allowNavigation("https://user@merchant.example/i"));
        assertFalse(policy.allowNavigation("http://merchant.example/i"));
    }
}
