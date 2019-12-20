package com.bcm.messenger.wallet.btc.net;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;

import okhttp3.OkHttpClient;

public class TorManagerOrbot extends TorManager {

    public TorManagerOrbot() {
        startClient();
    }

    @Override
    public void startClient() {
        // check if orbot is running - somehow
        setInitState("Checking Orbot", 1);
    }


    @Override
    public void stopClient() {

    }

    @Override
    public OkHttpClient setupClient(OkHttpClient client) {
        SocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 8118);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, proxyAddress);
        return client.newBuilder().proxy(proxy).build();
    }

    @Override
    public void resetInterface() {
        setInitState("Reset", 1);
    }
}
