package com.bcm.messenger.utility.bcmhttp.callback;

import okhttp3.Response;

public abstract class OriginCallback extends Callback<Response> {
    @Override
    public Response parseNetworkResponse(Response response, long id) throws Exception {
        return response;
    }
}
