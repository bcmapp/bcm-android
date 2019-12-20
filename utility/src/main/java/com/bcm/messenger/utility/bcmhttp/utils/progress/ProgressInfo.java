
package com.bcm.messenger.utility.bcmhttp.utils.progress;

import android.os.Parcel;
import android.os.Parcelable;

import com.bcm.messenger.utility.proguard.NotGuard;

public class ProgressInfo implements Parcelable, NotGuard {
    private long currentBytes;
    private long contentLength;
    private long intervalTime;
    private long eachBytes;
    private long id;
    private boolean finish;


    public ProgressInfo(long id) {
        this.id = id;
    }

    void setCurrentbytes(long currentbytes) {
        this.currentBytes = currentbytes;
    }

    void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    void setIntervalTime(long intervalTime) {
        this.intervalTime = intervalTime;
    }

    void setEachBytes(long eachBytes) {
        this.eachBytes = eachBytes;
    }

    void setFinish(boolean finish) {
        this.finish = finish;
    }


    public long getCurrentbytes() {
        return currentBytes;
    }

    public long getContentLength() {
        return contentLength;
    }

    public long getIntervalTime() {
        return intervalTime;
    }

    public long getEachBytes() {
        return eachBytes;
    }

    public long getId() {
        return id;
    }

    public boolean isFinish() {
        return finish;
    }


    public int getPercent() {
        if (getCurrentbytes() <= 0 || getContentLength() <= 0) return 0;
        return (int) ((100 * getCurrentbytes()) / getContentLength());
    }

    public long getSpeed() {
        if (getEachBytes() <= 0 || getIntervalTime() <= 0) return 0;
        return getEachBytes() * 1000 / getIntervalTime();
    }

    @Override
    public String toString() {
        return "ProgressInfo{" +
                "id=" + id +
                ", currentBytes=" + currentBytes +
                ", contentLength=" + contentLength +
                ", eachBytes=" + eachBytes +
                ", intervalTime=" + intervalTime +
                ", finish=" + finish +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.currentBytes);
        dest.writeLong(this.contentLength);
        dest.writeLong(this.intervalTime);
        dest.writeLong(this.eachBytes);
        dest.writeLong(this.id);
        dest.writeByte(this.finish ? (byte) 1 : (byte) 0);
    }

    protected ProgressInfo(Parcel in) {
        this.currentBytes = in.readLong();
        this.contentLength = in.readLong();
        this.intervalTime = in.readLong();
        this.eachBytes = in.readLong();
        this.id = in.readLong();
        this.finish = in.readByte() != 0;
    }

    public static final Creator<ProgressInfo> CREATOR = new Creator<ProgressInfo>() {
        @Override
        public ProgressInfo createFromParcel(Parcel source) {
            return new ProgressInfo(source);
        }

        @Override
        public ProgressInfo[] newArray(int size) {
            return new ProgressInfo[size];
        }
    };
}
