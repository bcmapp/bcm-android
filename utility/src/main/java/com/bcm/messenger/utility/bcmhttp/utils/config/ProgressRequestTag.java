package com.bcm.messenger.utility.bcmhttp.utils.config;

public class ProgressRequestTag extends RequestTag {

    protected boolean enableUploadProgress;
    protected boolean enableDownloadProgress;

    public ProgressRequestTag(boolean enableUploadProgress, boolean enableDownloadProgress) {
        super();
        this.enableDownloadProgress = enableDownloadProgress;
        this.enableUploadProgress = enableUploadProgress;
    }


    @Override
    public boolean enableUploadProgress() {
        return this.enableUploadProgress;
    }

    @Override
    public boolean enableDownloadProgress() {
        return this.enableDownloadProgress;
    }
}
