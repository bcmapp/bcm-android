package com.bcm.messenger.utility.bcmhttp.call.callbuilder.individualbodybuilder;

import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp;
import com.bcm.messenger.utility.bcmhttp.utils.config.RequestConst;

import java.io.File;

import okhttp3.OkHttpClient;

public abstract class StringCallBuilder extends SingleBodyCallBuilder {
    public StringCallBuilder(OkHttpClient client, BaseHttp service) {
        super(client, service);
    }

    @Override
    public SingleBodyCallBuilder file(File file) {
        return null;
    }

    @Override
    public SingleBodyCallBuilder content(String stringContent) {
        this.stringContent = stringContent;
        return this;
    }


    @Override
    protected boolean isFileContent() {
        return false;
    }


    public static class PutBuilder extends StringCallBuilder {
        public PutBuilder(OkHttpClient client, BaseHttp service) {
            super(client, service);
        }

        @Override
        protected String method() {
            return RequestConst.METHOD.PUT;
        }
    }

    public static class PostBuilder extends StringCallBuilder {
        public PostBuilder(OkHttpClient client, BaseHttp service) {
            super(client, service);
        }

        @Override
        protected String method() {
            return RequestConst.METHOD.POST;
        }
    }

    public static class PatchBuilder extends StringCallBuilder {
        public PatchBuilder(OkHttpClient client, BaseHttp service) {
            super(client, service);
        }

        @Override
        protected String method() {
            return RequestConst.METHOD.PATCH;
        }
    }

    public static class DeleteBuilder extends StringCallBuilder {
        public DeleteBuilder(OkHttpClient client, BaseHttp service) {
            super(client, service);
        }

        @Override
        protected String method() {
            return RequestConst.METHOD.DELETE;
        }
    }

}
