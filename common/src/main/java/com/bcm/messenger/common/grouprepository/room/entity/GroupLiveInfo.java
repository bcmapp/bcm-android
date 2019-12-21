package com.bcm.messenger.common.grouprepository.room.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import android.provider.BaseColumns;

import com.bcm.messenger.utility.AmeTimeUtil;

import static com.bcm.messenger.common.grouprepository.room.entity.GroupLiveInfo.TABLE_NAME;

@Entity(tableName = TABLE_NAME)
public class GroupLiveInfo {
    public static final String TABLE_NAME = "group_live_info";
    /**
     * The name of the ID column.
     */
    public static final String COLUMN_ID = BaseColumns._ID;


    /**
     * The unique ID of the groupMessage.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(index = true, name = COLUMN_ID)
    public long id;

    private long gid;

    //，，
    private boolean isLiving;

    private String source_url = "";//
    private int source_type;//
    private long start_time;//
    private long duration;//
    @ColumnInfo(index = true)
    private long liveId; //，

    private int liveStatus;//， LiveStatus 
    private long currentSeekTime; //，

    private long currentActionTime;//，，

    private boolean confirmed; //


    //
    public boolean isLiving() {
        return isLiving;
    }
    public void setLiving(boolean living) {
        isLiving = living;
    }


    public String getSource_url() {
        return source_url;
    }

    public void setSource_url(String source_url) {
        this.source_url = source_url;
    }

    public long getStart_time() {
        return start_time;
    }

    public void setStart_time(long start_time) {
        this.start_time = start_time;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getGid() {
        return gid;
    }

    public void setGid(long gid) {
        this.gid = gid;
    }

    public long getLiveId() {
        return liveId;
    }

    public void setLiveId(long liveId) {
        this.liveId = liveId;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public int getLiveStatus() {
        return liveStatus;
    }

    public void setLiveStatus(int liveStatus) {
        this.liveStatus = liveStatus;
    }

    public long getCurrentSeekTime() {
        return currentSeekTime;
    }

    public void setCurrentSeekTime(long currentSeekTime) {
        this.currentSeekTime = currentSeekTime;
    }


    public long getCurrentActionTime() {
        return currentActionTime;
    }

    public void setCurrentActionTime(long currentActionTime) {
        this.currentActionTime = currentActionTime;
    }

    public int getSource_type() {
        return source_type;
    }

    public void setSource_type(int source_type) {
        this.source_type = source_type;
    }


    public LiveSourceType sourceTypeForLive(){
        return LiveSourceType.valueForType(source_type);
    }

    public boolean isLiveStatus() {
        return liveStatus == LiveStatus.LIVING.getValue();
    }

    public boolean livePlayHasDone() {
        return isLiveStatus() && (AmeTimeUtil.INSTANCE.serverTimeMillis() - currentActionTime + currentSeekTime > duration);
    }


    public long computeLiveFinishTime() {
        return duration - (AmeTimeUtil.INSTANCE.serverTimeMillis() - currentActionTime + currentSeekTime);
    }


    /**
     * -2:，-1：，0：，1：，2：，3：
     */
    public enum LiveStatus {

        STASH(-2), REMOVED(-1), EMPTY(0), LIVING(1), PAUSE(2), STOPED(3);

        public static LiveStatus getStatus(int status) {
            switch (status) {
                case 0:
                    return EMPTY;
                case 1:
                    return LIVING;
                case -1:
                    return REMOVED;
                case 2:
                    return PAUSE;
                case 3:
                    return STOPED;
                case -2:
                    return STASH;
            }
            return LiveStatus.EMPTY;
        }

        LiveStatus(int value) {
            this.value = value;
        }

        private int value;

        public int getValue() {
            return value;
        }
    }

    /**
     * Unsupported :，Deprecated：，original，，，youtube，
     */
    public enum LiveSourceType {

        Unsupported(-1), Deprecated(0), Original(1), Youtube(2);

        private int value;


        public static LiveSourceType valueForType(int value) {
            switch (value) {
                case -1:
                    return Unsupported;
                case 0:
                    return Deprecated;
                case 1:
                    return Original;
                case 2:
                    return Youtube;
            }
            return Unsupported;
        }

        public int getValue() {
            return value;
        }

        LiveSourceType(int value) {
            this.value = value;
        }
    }

}
