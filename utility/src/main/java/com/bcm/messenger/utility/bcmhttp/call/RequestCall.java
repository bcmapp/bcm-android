package com.bcm.messenger.utility.bcmhttp.call;

import com.bcm.messenger.utility.bcmhttp.callback.Callback;
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp;
import com.bcm.messenger.utility.bcmhttp.utils.config.RequestTag;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class RequestCall {

    private Request request;
    private Call call;

    private RequestTag tag;

    private long readTimeOut;
    private long writeTimeOut;
    private long connTimeOut;
    private OkHttpClient client;
    private BaseHttp service;

    public RequestCall(OkHttpClient client, BaseHttp service, Request request) {
        this.client = client;

        this.service = service;


        if (request.tag() == null || !(request.tag() instanceof RequestTag)) {
            this.tag = new RequestTag();
            this.request = request.newBuilder().tag(this.tag).build();
        } else {
            this.tag = (RequestTag) request.tag();
            this.request = request;
        }
    }

    public RequestCall readTimeOut(long readTimeOut) {
        this.readTimeOut = readTimeOut;
        return this;
    }

    public RequestCall writeTimeOut(long writeTimeOut) {
        this.writeTimeOut = writeTimeOut;
        return this;
    }

    public RequestCall connTimeOut(long connTimeOut) {
        this.connTimeOut = connTimeOut;
        return this;
    }

    public Call buildCall() {
        OkHttpClient clone;
        if (readTimeOut > 0 || writeTimeOut > 0 || connTimeOut > 0) {
            readTimeOut = readTimeOut > 0 ? readTimeOut : BaseHttp.DEFAULT_MILLISECONDS;
            writeTimeOut = writeTimeOut > 0 ? writeTimeOut : BaseHttp.DEFAULT_MILLISECONDS;
            connTimeOut = connTimeOut > 0 ? connTimeOut : BaseHttp.DEFAULT_MILLISECONDS;

            clone = client.newBuilder()
                    .readTimeout(readTimeOut, TimeUnit.MILLISECONDS)
                    .writeTimeout(writeTimeOut, TimeUnit.MILLISECONDS)
                    .connectTimeout(connTimeOut, TimeUnit.MILLISECONDS)
                    .build();


        } else {
            clone = client.newBuilder().build();
        }
        call = clone.newCall(request);
        return call;
    }

    public long enqueue(Callback callback) {
        buildCall();
        service.post(this, callback);
        return getId();
    }

    public Response execute() throws IOException {
        return buildCall().execute();
    }

    public void cancel() {
        if (call != null) {
            call.cancel();
        }
    }


    public Call getCall() {
        return call;
    }

    public Request getRequest() {
        return request;
    }

    public RequestTag getTag() {
        return tag;
    }

    public long getId() {
        return this.tag.getId();
    }

    public boolean enableUploadProgress() {
        return getTag().enableUploadProgress();
    }

    public boolean enableDownloadProgress() {
        return getTag().enableDownloadProgress();
    }


}
