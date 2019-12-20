package com.bcm.messenger.utility.bcmhttp.call.callbuilder.bodyabsentbuilder;

import android.net.Uri;
import com.bcm.messenger.utility.bcmhttp.call.RequestCall;
import com.bcm.messenger.utility.bcmhttp.call.callbuilder.BaseCallBuilder;
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import okhttp3.OkHttpClient;
public abstract class BodyAbsentCallBuilder extends BaseCallBuilder<BodyAbsentCallBuilder> {

    protected Map<String, String> params;
    protected String url;


    public BodyAbsentCallBuilder(OkHttpClient client, BaseHttp service) {
        super(client, service);
    }

    @Override
    public RequestCall build() {
        return new RequestCall(client, service, requestBuilder
                .url(appendParams(url, params))
                .method(this.method(), null)
                .build());
    }


    @Override
    public BodyAbsentCallBuilder url(String url) {
        this.url = url;
        return this;
    }


    protected String appendParams(String url, Map<String, String> params) {
        if (url == null || params == null || params.isEmpty()) {
            return url;
        }
        Uri.Builder builder = Uri.parse(url).buildUpon();
        Set<String> keys = params.keySet();
        Iterator<String> iterator = keys.iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            builder.appendQueryParameter(key, params.get(key));
        }
        return builder.build().toString();
    }


    public BodyAbsentCallBuilder params(Map<String, String> params) {
        this.params = params;
        return this;
    }

    public BodyAbsentCallBuilder addParam(String key, String val) {
        if (this.params == null) {
            params = new LinkedHashMap<>();
        }
        params.put(key, val);
        return this;
    }
}
