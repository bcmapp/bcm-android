package com.bcm.messenger.utility.bcmhttp.callback;

import java.io.IOException;

import okhttp3.Response;
import okhttp3.ResponseBody;

public abstract class StringCallback extends Callback<String> {
    @Override
    public String parseNetworkResponse(Response response, long id) throws IOException {
        ResponseBody body = response.body();
        String payloadString = "";
        if (null != body) {
            payloadString = body.string();
        }
        return payloadString;
    }

}
