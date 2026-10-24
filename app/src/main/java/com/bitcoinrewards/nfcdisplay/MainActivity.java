package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private WebView webView; private TextView loadingText; private WebOriginPolicy policy;
    @Override protected void onCreate(Bundle state){super.onCreate(state);LegacyCredentialPurge.run(this,()->initialize(state));}
    private void initialize(Bundle state){if(!SettingsActivity.configurationUsable(this)){startActivity(new Intent(this,SettingsActivity.class));finish();return;}String display=SettingsActivity.getDisplayUrl(this);try{policy=new WebOriginPolicy(SettingsActivity.getBtcpayUrl(this));}catch(Exception e){startActivity(new Intent(this,SettingsActivity.class));finish();return;}getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setContentView(R.layout.activity_main);webView=findViewById(R.id.webview);loadingText=findViewById(R.id.loading_text);LinearLayout panel=findViewById(R.id.rewards_scanner_panel);panel.setVisibility(View.VISIBLE);Button scanner=findViewById(R.id.btn_open_rewards_scanner);scanner.setOnClickListener(v->startActivity(new Intent(this, RewardsProfileScanActivity.class)));ImageButton settings=findViewById(R.id.btn_settings);settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setCacheMode(WebSettings.LOAD_DEFAULT);CookieManager.getInstance().setAcceptCookie(true);if(android.os.Build.VERSION.SDK_INT>=21)CookieManager.getInstance().setAcceptThirdPartyCookies(webView,false);
        webView.setWebChromeClient(new WebChromeClient(){
            @Override @android.annotation.TargetApi(21)
            public void onPermissionRequest(PermissionRequest request){runOnUiThread(()->{String origin=request.getOrigin()==null?"":request.getOrigin().toString();boolean camera=false;for(String resource:request.getResources())if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource))camera=true;if(camera&&policy.allowCamera(origin))request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});else request.deny();});}
        });
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){super.onPageStarted(view,url,icon);}
            @Override @android.annotation.TargetApi(21)
            public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return request!=null&&request.isForMainFrame()&&!policy.allowNavigation(request.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return !policy.allowNavigation(url);}
            @Override public void onPageFinished(WebView view,String url){loadingText.setVisibility(View.GONE);view.setVisibility(View.VISIBLE);}
        });
        webView.setVisibility(View.INVISIBLE);loadingText.setVisibility(View.VISIBLE);webView.loadUrl(display);
    }
    @Override protected void onDestroy(){if(webView!=null)webView.destroy();super.onDestroy();}
}
