package com.bcm.messenger.utility.bcmhttp.facade;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.utility.bcmhttp.call.RequestCall;
import com.bcm.messenger.utility.bcmhttp.call.callbuilder.bodyabsentbuilder.GetCallBuilder;
import com.bcm.messenger.utility.bcmhttp.call.callbuilder.individualbodybuilder.FileCallBuilder;
import com.bcm.messenger.utility.bcmhttp.call.callbuilder.individualbodybuilder.StringCallBuilder;
import com.bcm.messenger.utility.bcmhttp.callback.Callback;
import com.bcm.messenger.utility.bcmhttp.exception.ConnectionException;
import com.bcm.messenger.utility.bcmhttp.utils.config.Platform;
import com.bcm.messenger.utility.bcmhttp.utils.progress.ProgressInfo;
import com.bcm.messenger.utility.bcmhttp.utils.progress.ProgressListener;
import com.bcm.messenger.utility.bcmhttp.utils.progress.ProgressManager;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executor;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class BaseHttp {
    public static final long DEFAULT_MILLISECONDS = 10_000L;
    public static final long DEFAULT_FILE_MILLISECONDS = 60_000L;

    private Platform mPlatform;
    protected OkHttpClient client;
    private String frontDomain;

    private static Boolean devMode = false;

    public static void setDevMode(Boolean isDev) {
        devMode = isDev;
    }

    public BaseHttp() {
        mPlatform = Platform.get();
    }

    public void setClient(OkHttpClient client) {
        if (!devMode) {
            this.client = client;
        } else {
            OkHttpClient.Builder devClientBuilder = client.newBuilder()
                    .sslSocketFactory(trustAllSSLFactory())
                    .hostnameVerifier(trustAllHostVerify());
            this.client = devClientBuilder.build();
        }
    }

    protected OkHttpClient getClient() {
        return client;
    }

    public void setFrontDomain(String domain) {
        this.frontDomain = domain;
    }

    public String getFrontDomain() {
        return frontDomain;
    }

    public Executor getDelivery() {
        return mPlatform.defaultCallbackExecutor();
    }

    public GetCallBuilder get() {
        return new GetCallBuilder(client, this);
    }

    public FileCallBuilder.PostFormBuilder postForm() {
        return new FileCallBuilder.PostFormBuilder(client, this);
    }

    public FileCallBuilder.PutBuilder putFile() {
        return new FileCallBuilder.PutBuilder(client, this);
    }

    public StringCallBuilder.PutBuilder putString() {
        return new StringCallBuilder.PutBuilder(client, this);
    }

    public StringCallBuilder.DeleteBuilder deleteString() {
        return new StringCallBuilder.DeleteBuilder(client, this);
    }

    public StringCallBuilder.PatchBuilder patchString() {
        return new StringCallBuilder.PatchBuilder(client, this);
    }

    public StringCallBuilder.PostBuilder postString() {
        return new StringCallBuilder.PostBuilder(client, this);
    }

    public void post(@NonNull final RequestCall requestCall, @Nullable Callback callback) {
        if (callback == null)
            callback = Callback.CALLBACK_DEFAULT;
        final Callback finalCallback = callback;
        final long id = requestCall.getId();
        if (requestCall.enableUploadProgress()) {
            ProgressManager.getInstance().registerRequestListener(id, new ProgressListener() {
                @Override
                public void onProgress(final ProgressInfo progressInfo) {
                    broadcastProgressCallback(finalCallback, id, progressInfo);
                }
            });
        } else if (requestCall.enableDownloadProgress()) {
            ProgressManager.getInstance().registerResponseListener(id, new ProgressListener() {
                @Override
                public void onProgress(final ProgressInfo progressInfo) {
                    broadcastProgressCallback(finalCallback, id, progressInfo);
                }
            });
        }

        requestCall.getCall().enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                ProgressManager.getInstance().unregisterProgressListener(id);
                sendFailResultCallback(call, e, finalCallback, id);
            }

            @Override
            public void onResponse(final Call call, final Response response) {
                ProgressManager.getInstance().unregisterProgressListener(id);
                try {
                    if (call.isCanceled()) {
                        sendFailResultCallback(call, new HttpErrorException(0, ""), finalCallback, id);
                        return;
                    }

                    if (!finalCallback.validateResponse(response, id)) {
                        Exception error = new HttpErrorException(response.code(), response.message());
                        sendFailResultCallback(call, error, finalCallback, id);
                        return;
                    }

                    Object o = finalCallback.parseNetworkResponse(response, id);
                    sendSuccessResultCallback(o, finalCallback, id);
                } catch (Exception e) {
                    sendFailResultCallback(call, e, finalCallback, id);
                } finally {
                    if (response.body() != null)
                        response.body().close();
                }
            }
        });
    }

    protected void broadcastProgressCallback(final Callback callback, final long id, final ProgressInfo progressInfo) {
        if (callback == null) return;
        switch (callback.callInThreadMode()) {
            case Callback.THREAD_MAIN: {
                mPlatform.execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.inProgress(progressInfo.getPercent(), progressInfo.getContentLength(), id);
                        Log.e("HTTP Facade", "===== upload percent ===" + progressInfo.getPercent());
                    }
                });
                break;
            }
            case Callback.THREAD_SYNC: {
                getDelivery().execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.inProgress(progressInfo.getPercent(), progressInfo.getContentLength(), id);
                        Log.e("HTTP Facade", "===== upload percent ===" + progressInfo.getPercent());
                    }
                });
                break;
            }
            case Callback.THREAD_CURRENT: {
                callback.inProgress(progressInfo.getPercent(), progressInfo.getContentLength(), id);
                Log.e("HTTP Facade", "===== upload percent ===" + progressInfo.getPercent());
                break;
            }
            default:
                break;
        }
    }

    protected void sendFailResultCallback(final Call call, final Exception e, final Callback callback, final long id) {
        if (callback == null) return;

        Runnable r = () -> {
            if (e instanceof ConnectException || e instanceof SocketTimeoutException) {
                callback.onError(call, new ConnectionException(e), id);
            } else {
                callback.onError(call, e, id);
            }
        };

        switch (callback.callInThreadMode()) {
            case Callback.THREAD_MAIN: {
                mPlatform.execute(r);
                break;
            }
            case Callback.THREAD_SYNC: {
                getDelivery().execute(r);
                break;
            }
            case Callback.THREAD_CURRENT: {
                r.run();
                break;
            }
            default:
                break;
        }
    }

    protected void sendSuccessResultCallback(final Object object, final Callback callback, final long id) {
        if (callback == null) return;


        switch (callback.callInThreadMode()) {
            case Callback.THREAD_MAIN: {
                mPlatform.execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResponse(object, id);
                    }
                });
                break;
            }
            case Callback.THREAD_SYNC: {
                getDelivery().execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResponse(object, id);
                    }
                });
                break;
            }
            case Callback.THREAD_CURRENT: {
                callback.onResponse(object, id);
                break;
            }
            default: {
                break;
            }
        }
    }

    private SSLSocketFactory trustAllSSLFactory() {
        SSLSocketFactory ssfFactory = null;
        try {
            SSLContext sc = SSLContext.getInstance("TLS");

            MyTrustManager[] trustManager = {new MyTrustManager()};
            sc.init(null, trustManager, new SecureRandom());
            ssfFactory = sc.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ssfFactory;
    }

    public static HostnameVerifier trustAllHostVerify() {
        return new TrustAllHostVerify();
    }

    private static class TrustAllHostVerify implements HostnameVerifier {

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }

    private class MyTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    public static class HttpErrorException extends Exception {
        public int code;
        public String describe;

        public HttpErrorException(int code, String describe) {
            super(describe);

            this.code = code;
            this.describe = describe;
        }

        public HttpErrorException(int code, String describe, Exception e) {
            super(describe, e);

            this.code = code;
            this.describe = describe;
        }
    }

}

