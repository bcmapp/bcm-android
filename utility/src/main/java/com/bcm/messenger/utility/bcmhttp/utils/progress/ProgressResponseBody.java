
package com.bcm.messenger.utility.bcmhttp.utils.progress;

import android.os.SystemClock;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

public class ProgressResponseBody extends ResponseBody {
    protected int mRefreshTime;
    protected final ResponseBody responseBody;
    protected final ProgressListener mListener;
    protected final ProgressInfo mProgressInfo;
    private BufferedSource mBufferedSource;

    public ProgressResponseBody(ResponseBody responseBody, ProgressListener listener, int refreshTime) {
        this.responseBody = responseBody;
        this.mListener = listener;
        this.mRefreshTime = refreshTime;
        this.mProgressInfo = new ProgressInfo(System.currentTimeMillis());
    }

    @Override
    public MediaType contentType() {
        return responseBody.contentType();
    }

    @Override
    public long contentLength() {
        return responseBody.contentLength();
    }

    @Override
    public BufferedSource source() {
        if (mBufferedSource == null) {
            mBufferedSource = Okio.buffer(source(responseBody.source()));
        }
        return mBufferedSource;
    }

    private Source source(Source source) {
        return new ForwardingSource(source) {
            private long totalBytesRead = 0L;
            private long lastRefreshTime = 0L;
            private long tempSize = 0L;

            @Override
            public long read(Buffer sink, long byteCount) throws IOException {
                long bytesRead = 0L;
                bytesRead = super.read(sink, byteCount);
                if (mProgressInfo.getContentLength() == 0) {
                    mProgressInfo.setContentLength(contentLength());
                }
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                tempSize += bytesRead != -1 ? bytesRead : 0;
                if (mListener != null) {
                    long curTime = SystemClock.elapsedRealtime();
                    if (curTime - lastRefreshTime >= mRefreshTime || bytesRead == -1 || totalBytesRead == mProgressInfo.getContentLength()) {
                        final long finalBytesRead = bytesRead;
                        final long finalTempSize = tempSize;
                        final long finalTotalBytesRead = totalBytesRead;
                        final long finalIntervalTime = curTime - lastRefreshTime;
                        mProgressInfo.setEachBytes(finalBytesRead != -1 ? finalTempSize : -1);
                        mProgressInfo.setCurrentbytes(finalTotalBytesRead);
                        mProgressInfo.setIntervalTime(finalIntervalTime);
                        mProgressInfo.setFinish(finalBytesRead == -1 && finalTotalBytesRead == mProgressInfo.getContentLength());
                        mListener.onProgress(mProgressInfo);
                        lastRefreshTime = curTime;
                        tempSize = 0;
                    }
                }
                return bytesRead;
            }
        };
    }
}
