package com.bcm.messenger.utility.bcmhttp.utils.config;

import okhttp3.MediaType;

public class RequestConst {

    public static class METHOD {
        public static final String HEAD = "HEAD";
        public static final String DELETE = "DELETE";
        public static final String PUT = "PUT";
        public static final String PATCH = "PATCH";
        public static final String POST = "POST";
        public static final String GET = "GET";
    }

    public static class BodySequenceType {
        public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        public static final MediaType PLAIN = MediaType.parse("text/plain; charset=utf-8");
        public static final MediaType STREAM = MediaType.parse("application/octet-stream");

    }

}
