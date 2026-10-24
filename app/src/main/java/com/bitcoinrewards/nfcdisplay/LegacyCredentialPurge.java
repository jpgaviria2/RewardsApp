package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.CookieManager;

/** Fail-closed one-time upgrade: no activity UI is enabled until secrets and cookies are gone. */
final class LegacyCredentialPurge {
    private static final String LEGACY_PREFS="RewardsNfcPrefs";
    private static final String COMPLETE="legacy_credential_purge_complete_v2";
    private static final String PENDING="legacy_credential_purge_pending_v2";
    private static final String[] LEGACY_KEYS={"email","password","api_key","auth_method","session_cookies","store_name","refresh_seconds","nfc_enabled"};

    static void run(Activity activity,Runnable ready){
        SharedPreferences current=activity.getSharedPreferences(SettingsActivity.PREFS_NAME,Activity.MODE_PRIVATE);
        if(current.getBoolean(COMPLETE,false)&&!current.getBoolean(PENDING,false)){ready.run();return;}
        if(!current.edit().putBoolean(PENDING,true).putBoolean(SettingsActivity.KEY_ONBOARDED,false).commit())return;
        SharedPreferences legacy=activity.getSharedPreferences(LEGACY_PREFS,Activity.MODE_PRIVATE);
        if(!legacy.edit().clear().commit())return;
        SharedPreferences.Editor editor=current.edit();
        for(String key:LEGACY_KEYS)editor.remove(key);
        if(!editor.commit())return;
        CookieManager cookies=CookieManager.getInstance();
        Runnable finish=()->{
            if(Build.VERSION.SDK_INT>=21)cookies.flush();
            if(current.edit().putBoolean(COMPLETE,true).putBoolean(PENDING,false).commit())ready.run();
        };
        if(Build.VERSION.SDK_INT>=21)cookies.removeAllCookies(value->activity.runOnUiThread(finish));
        else { cookies.removeAllCookie(); finish.run(); }
    }

    private LegacyCredentialPurge(){}
}
