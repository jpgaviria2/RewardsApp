package com.bitcoinrewards.nfcdisplay;

final class WebOriginPolicy {
    private final String configured;
    WebOriginPolicy(String configured){this.configured=ApiOrigin.requireConfigured(configured).toString();}
    boolean allowNavigation(String url){return ApiOrigin.sameOrigin(configured,url);}
    boolean allowCamera(String origin){return ApiOrigin.sameOrigin(configured,origin);}
}
