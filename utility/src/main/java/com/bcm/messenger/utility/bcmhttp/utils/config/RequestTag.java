package com.bcm.messenger.utility.bcmhttp.utils.config;

public class RequestTag {

    protected long id;

    public RequestTag(long id) {
        this.id = id;
    }

    public RequestTag() {
        this(generateRequestId());
    }

    public long getId() {
        return id;
    }

    public boolean enableUploadProgress() {
        return false;
    }

    public boolean enableDownloadProgress() {
        return false;
    }

    protected static long generateRequestId() {
        return System.currentTimeMillis();
    }
}
