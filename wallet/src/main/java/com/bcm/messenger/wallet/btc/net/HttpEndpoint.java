package com.bcm.messenger.wallet.btc.net;

import java.net.URI;
import okhttp3.OkHttpClient;

public class HttpEndpoint {
    private final String baseUrlString;

    public HttpEndpoint(String baseUrlString) {
        this.baseUrlString = baseUrlString;
    }

    @Override
    public String toString() {
        return getBaseUrl();
    }

    public String getBaseUrl() {
        return baseUrlString;
    }

    public URI getUri(String basePath, String function) {
        return URI.create(this.getBaseUrl() + basePath + '/' + function);
    }

    public URI getUri(String function) {
        return URI.create(this.getBaseUrl() + '/' + function);
    }

    public OkHttpClient getClient() {
        return new OkHttpClient.Builder().build();
    }

}
