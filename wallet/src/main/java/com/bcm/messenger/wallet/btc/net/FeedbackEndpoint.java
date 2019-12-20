package com.bcm.messenger.wallet.btc.net;

public interface FeedbackEndpoint {
    void onError();

    void onSuccess();
}
