package com.bcm.messenger.wallet.btc.net;

import com.bcm.messenger.wallet.btc.util.SslUtils;

import javax.net.ssl.SSLSocketFactory;

import okhttp3.OkHttpClient;

public class HttpsEndpoint extends HttpEndpoint {
    public final String certificateThumbprint;

    public HttpsEndpoint(String baseUrlString, String certificateThumbprint) {
        super(baseUrlString);
        this.certificateThumbprint = certificateThumbprint;
    }

    public SSLSocketFactory getSslSocketFactory() {
        return SslUtils.getSsLSocketFactory(certificateThumbprint);
    }

    @Override
    public OkHttpClient getClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.sslSocketFactory(this.getSslSocketFactory());
        builder.hostnameVerifier(SslUtils.HOST_NAME_VERIFIER_ACCEPT_ALL);
        return builder.build();
    }
}
