package com.bitcoinrewards.nfcdisplay;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Polls the BTCPay Bitcoin Rewards plugin API for pending rewards.
 */
public class RewardPoller {
    private static final String TAG = "RewardPoller";
    private static final long POLL_INTERVAL_MS = 5000;

    public interface RewardCallback {
        void onReward(String rewardId, long sats, String lnurl, String qrDataUri, int remainingSeconds);
        void onNoReward();
    }

    private final String btcpayUrl;
    private final String storeId;
    private final String apiKey;
    private final RewardCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            new Thread(() -> poll()).start();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public RewardPoller(String btcpayUrl, String storeId, String apiKey, RewardCallback callback) {
        this.btcpayUrl = btcpayUrl;
        this.storeId = storeId;
        this.apiKey = apiKey;
        this.callback = callback;
    }

    public void start() {
        running = true;
        handler.post(pollRunnable);
    }

    public void destroy() {
        running = false;
        handler.removeCallbacks(pollRunnable);
    }

    private void poll() {
        try {
            String endpoint = btcpayUrl + "/plugins/bitcoin-rewards/" + storeId + "/display-data";
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            if (code != 200) {
                handler.post(() -> callback.onNoReward());
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            boolean hasReward = json.optBoolean("hasReward", false);

            if (hasReward) {
                String rewardId = json.getString("rewardId");
                long sats = json.getLong("rewardAmountSatoshis");
                String lnurl = json.getString("lnurlString");
                String qrDataUri = json.getString("lnurlQrDataUri");
                int remaining = json.optInt("remainingSeconds", 30);
                handler.post(() -> callback.onReward(rewardId, sats, lnurl, qrDataUri, remaining));
            } else {
                handler.post(() -> callback.onNoReward());
            }
        } catch (Exception e) {
            Log.e(TAG, "Poll error: " + e.getMessage());
            handler.post(() -> callback.onNoReward());
        }
    }
}
