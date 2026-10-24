package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardsPagePolicyTest {
    @Test public void onlyConfiguredStoreDisplayAndCheckInPagesEnableAutomaticCamera() {
        RewardsPagePolicy policy = new RewardsPagePolicy("https://btcpay.example", "store */?é");
        String base = "https://btcpay.example/plugins/bitcoin-rewards/store%20%2A%2F%3F%C3%A9";
        assertTrue(policy.isTrustedRewardsPage(base + "/display"));
        assertTrue(policy.isTrustedRewardsPage(base + "/display?state=waiting"));
        assertTrue(policy.isTrustedRewardsPage(base + "/check-in"));
        assertTrue(policy.isTrustedRewardsPage(base + "/check-in/customer"));
        assertFalse(policy.isTrustedRewardsPage("https://btcpay.example/login"));
        assertFalse(policy.isTrustedRewardsPage("https://btcpay.example/account"));
        assertFalse(policy.isTrustedRewardsPage("https://btcpay.example/plugins/bitcoin-rewards/other/display"));
        assertFalse(policy.isTrustedRewardsPage("https://evil.example/plugins/bitcoin-rewards/store%20%2A%2F%3F%C3%A9/display"));
    }

    @Test public void completedRewardsPageMustStillBeCurrentMainFrame() {
        RewardsPagePolicy policy = new RewardsPagePolicy("https://btcpay.example", "store");
        String display = "https://btcpay.example/plugins/bitcoin-rewards/store/display";

        assertTrue(policy.isEligiblePageCompletion(display, display));
        assertFalse(policy.isEligiblePageCompletion(display, "https://btcpay.example/login"));
        assertFalse(policy.isEligiblePageCompletion(display, "https://btcpay.example/account"));
    }

    @Test public void matchingUntrustedOrMissingCompletionsRemainIneligible() {
        RewardsPagePolicy policy = new RewardsPagePolicy("https://btcpay.example", "store");
        String login = "https://btcpay.example/login";

        assertFalse(policy.isEligiblePageCompletion(login, login));
        assertFalse(policy.isEligiblePageCompletion(null, login));
        assertFalse(policy.isEligiblePageCompletion(login, null));
    }
}
