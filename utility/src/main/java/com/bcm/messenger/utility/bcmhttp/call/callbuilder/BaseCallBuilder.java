package com.bcm.messenger.utility.bcmhttp.call.callbuilder;

import com.bcm.messenger.utility.bcmhttp.call.RequestCall;
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp;
import com.bcm.messenger.utility.bcmhttp.utils.config.RequestTag;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * @param <T>
 */
public abstract class BaseCallBuilder<T extends BaseCallBuilder> {

    protected Request.Builder requestBuilder = new Request.Builder();
    protected OkHttpClient client;
    protected BaseHttp service;

    public BaseCallBuilder(OkHttpClient client, BaseHttp service) {
        this.client = client;
        this.service = service;

        String frontDomain = service.getFrontDomain();
        if (null != frontDomain && !frontDomain.isEmpty()) {
            addHeader("HOST", frontDomain);
        }
    }


    public T url(String url) {
        requestBuilder.url(url);
        return (T) this;
    }

    public T tag(RequestTag tag) {
        requestBuilder.tag(tag);
        return (T) this;
    }

    public T addHeader(String key, String val) {
        requestBuilder.header(key, val);
        return (T) this;
    }

    protected abstract String method();

    public abstract RequestCall build();
}
