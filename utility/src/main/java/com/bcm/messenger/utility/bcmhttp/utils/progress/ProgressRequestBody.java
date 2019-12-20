
package com.bcm.messenger.utility.bcmhttp.utils.progress;

import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

public class ProgressRequestBody extends RequestBody {
    protected int mRefreshIntervalTime;
    protected final RequestBody mDelegate;
    protected final ProgressListener mListener;
    protected final ProgressInfo mProgressInfo;
    private BufferedSink mBufferedSink;


    public ProgressRequestBody(RequestBody delegate, ProgressListener listener, int refreshTime) {
        this.mDelegate = delegate;
        this.mListener = listener;
        this.mRefreshIntervalTime = refreshTime;
        this.mProgressInfo = new ProgressInfo(System.currentTimeMillis());
    }

    @Override
    public MediaType contentType() {
        return mDelegate.contentType();
    }

    @Override
    public long contentLength() {
        try {
            return mDelegate.contentLength();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        if (mBufferedSink == null) {
            mBufferedSink = Okio.buffer(new CountingSink(sink));
        }
        try {
            mDelegate.writeTo(mBufferedSink);
            mBufferedSink.flush();
        }catch (Exception e){
            Log.e("progress request", e.toString());
        }

    }

    protected final class CountingSink extends ForwardingSink {
        private long totalBytesRead = 0L;
        private long lastRefreshTime = 0L;
        private long tempSize = 0L;

        public CountingSink(Sink requestBody) {
            super(requestBody);
        }

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
            try {
                super.write(source, byteCount);
            } catch (Exception e) {
                Log.e("progress request", e.toString());
            }

            if (mProgressInfo.getContentLength() == 0) {
                mProgressInfo.setContentLength(contentLength());
            }
            totalBytesRead += byteCount;
            tempSize += byteCount;
            if (mListener != null) {
                long curTime = SystemClock.elapsedRealtime();
                if (curTime - lastRefreshTime >= mRefreshIntervalTime || totalBytesRead == mProgressInfo.getContentLength()) {
                    final long finalTempSize = tempSize;
                    final long finalTotalBytesRead = totalBytesRead;
                    final long finalIntervalTime = curTime - lastRefreshTime;
                    mProgressInfo.setEachBytes(finalTempSize);
                    mProgressInfo.setCurrentbytes(finalTotalBytesRead);
                    mProgressInfo.setIntervalTime(finalIntervalTime);
                    mProgressInfo.setFinish(finalTotalBytesRead == mProgressInfo.getContentLength());
                    mListener.onProgress(mProgressInfo);
                }
                lastRefreshTime = curTime;
                tempSize = 0;
            }
        }
    }
}
