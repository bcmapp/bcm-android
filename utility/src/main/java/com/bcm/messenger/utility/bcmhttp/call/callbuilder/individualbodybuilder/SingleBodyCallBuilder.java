package com.bcm.messenger.utility.bcmhttp.call.callbuilder.individualbodybuilder;

import com.bcm.messenger.utility.bcmhttp.call.RequestCall;
import com.bcm.messenger.utility.bcmhttp.call.callbuilder.BaseCallBuilder;
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp;
import com.bcm.messenger.utility.bcmhttp.utils.config.RequestConst;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public abstract class SingleBodyCallBuilder extends BaseCallBuilder<SingleBodyCallBuilder> {

    protected File fileContent;
    protected MediaType mediaType;//body mimiType
    protected String stringContent;

    public SingleBodyCallBuilder(OkHttpClient client, BaseHttp service) {
        super(client, service);
    }

    @Override
    public RequestCall build(){
        if (isFileContent()) {
            return new RequestCall(client, service, requestBuilder
                    .method(method(), RequestBody.create(mediaType, fileContent))
                    .build());
        } else {
            return new RequestCall(client, service, requestBuilder
                    .method(method(), RequestBody.create(mediaType, stringContent))
                    .build());
        }
    }

    public SingleBodyCallBuilder mediaType(MediaType mediaType) {
        if (mediaType == null) {
            this.mediaType = RequestConst.BodySequenceType.PLAIN;
        } else {
            this.mediaType = mediaType;
        }
        return this;
    }

    public abstract SingleBodyCallBuilder file(File file);

    public abstract SingleBodyCallBuilder content(String stringContent);

    protected abstract boolean isFileContent();
}
