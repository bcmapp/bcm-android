package com.bcm.messenger.utility.bcmhttp.interceptor;

import com.bcm.messenger.utility.bcmhttp.utils.config.RequestTag;
import com.bcm.messenger.utility.bcmhttp.utils.progress.ProgressListener;
import com.bcm.messenger.utility.bcmhttp.utils.progress.ProgressManager;
import com.bcm.messenger.utility.bcmhttp.utils.progress.ProgressRequestBody;
import com.bcm.messenger.utility.bcmhttp.utils.progress.ProgressResponseBody;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class ProgressInterceptor implements Interceptor {

    private static String TAG = "ProgressInterceptor";

    @Override
    public Response intercept(Chain chain) throws IOException {
        return wrapResponse(chain.proceed(wrapRequest(chain.request())));
    }

    private Response wrapResponse(Response response) {
        if (response == null || response.body() == null || !(response.request().tag() instanceof RequestTag)) {
            return response;
        }
        RequestTag tag = (RequestTag) response.request().tag();
        if (tag.enableDownloadProgress() && !tag.enableUploadProgress()) {
            if (ProgressManager.getInstance().getResponseListener().containsKey(tag.getId())) {
                ProgressListener listener = ProgressManager.getInstance().getResponseListener().get(tag.getId());
                return response.newBuilder()
                        .body(new ProgressResponseBody(response.body(), listener, 30))
                        .build();
            }
        }

        return response;
    }

    private Request wrapRequest(Request request) {
        if (request == null || request.body() == null || !(request.tag() instanceof RequestTag)) {
            return request;
        }
        RequestTag tag = (RequestTag) request.tag();
        if (tag.enableUploadProgress() && !tag.enableDownloadProgress()) {
            if (ProgressManager.getInstance().getRequestListener().containsKey(tag.getId())) {
                ProgressListener listener = ProgressManager.getInstance().getRequestListener().get(tag.getId());
                return request.newBuilder()
                        .method(request.method(), new ProgressRequestBody(request.body(), listener, 30))
                        .build();
            }
        }
        return request;
    }


}
