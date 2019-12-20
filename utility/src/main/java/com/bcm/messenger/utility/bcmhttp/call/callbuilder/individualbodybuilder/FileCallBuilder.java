package com.bcm.messenger.utility.bcmhttp.call.callbuilder.individualbodybuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.utility.bcmhttp.call.RequestCall;
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp;
import com.bcm.messenger.utility.bcmhttp.utils.config.RequestConst;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public abstract class FileCallBuilder extends SingleBodyCallBuilder {

    public FileCallBuilder(OkHttpClient client, BaseHttp service) {
        super(client, service);
    }

    @Override
    public SingleBodyCallBuilder content(String stringContent) {
        return null;
    }

    @Override
    public FileCallBuilder file(File file) {
        this.fileContent = file;
        return this;
    }

    @Override
    protected boolean isFileContent() {
        return true;
    }


    public static class PostBuilder extends FileCallBuilder {
        public PostBuilder(OkHttpClient client, BaseHttp service) {
            super(client, service);
        }

        @Override
        protected String method() {
            return RequestConst.METHOD.POST;
        }
    }


    public static class PutBuilder extends FileCallBuilder {
        public PutBuilder(OkHttpClient client, BaseHttp service) {
            super(client, service);
        }

        @Override
        protected String method() {
            return RequestConst.METHOD.PUT;
        }
    }

    public static class PostFormBuilder extends FileCallBuilder {
        private MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        public PostFormBuilder(OkHttpClient client, BaseHttp service) {
            super(client, service);
        }

        @Override
        protected String method() {
            return RequestConst.METHOD.POST;
        }

        public PostFormBuilder addFormData(@NonNull String key, @NonNull String value) {
            builder.addFormDataPart(key, value);
            return this;
        }

        public PostFormBuilder addFormFile(@NonNull String key, @NonNull String fileName, @NonNull File file, @NonNull String mimeType) {
            builder.addFormDataPart(key, fileName, RequestBody.create(MediaType.get(mimeType), file));
            return this;
        }

        public PostFormBuilder addFormPart(@NonNull String key, @Nullable String fileName, @NonNull RequestBody body) {
            builder.addFormDataPart(key, fileName, body);
            return this;
        }

        @Override
        public FileCallBuilder file(File file) {
            addFormFile("file", file.getName(), file, "image/*");
            return this;
        }

        @Override
        public RequestCall build() {
            return new RequestCall(client, service, requestBuilder.method(method(), builder.build()).build());
        }
    }

}
