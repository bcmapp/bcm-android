package com.bcm.messenger.common.grouprepository.room.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import android.provider.BaseColumns;

import com.bcm.messenger.utility.Base64;

import static com.bcm.messenger.common.grouprepository.room.entity.GroupMessage.TABLE_NAME;

@Entity(tableName = TABLE_NAME)
public class GroupMessage {
    public static final String TABLE_NAME = "group_message";
    /**
     * The name of the ID column.
     */
    public static final String COLUMN_ID = BaseColumns._ID;

    /**
     * The unique ID of the groupMessage.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    public long id;

    //
    public static final int READ_STATE_UNREAD = 0;
    public static final int READ_STATE_READ = 1;

    //
    public static final int CONFIRM_MESSAGE = 0;//
    public static final int UNCONFIRM_MESSAGE = 1;// 
    public static final int CONFIRM_BUT_NOT_SHOW = 2;//，
    public static final int DELETED_MESSAGE = 3;//，
    public static final int FETCHING_MESSAGE = 4;//

    //
    public static final int SEND = 0;
    public static final int RECEIVE = 1;

    //
    public static final int SEND_SUCCESS = 1;
    public static final int SENDING = 2;
    public static final int RECEIVE_SUCCESS = 3;
    public static final int SEND_FAILURE = 10000;

    /**
     * group_id
     */
    @ColumnInfo(index = true)
    private long gid;


    private long mid;

    /**
     *  id
     */
    private String from_uid;


    public static final int CHAT_MESSAGE = 1;
    public static final int PUB_MESSAGE = 2;
    /**
     * type: 1.chat ，，; 2.pub ，
     * default：1
     */
    private int type = 1;


    /**
     * message content
     */
    private String text = "";


    /**
     * 1.；2.  3.10000 
     */
    private int send_state;

    /**
     * 0：，1：
     */
    private int read_state = READ_STATE_UNREAD;


    //，
    private int is_confirm = CONFIRM_MESSAGE;


    private String attachment_uri;
    // Size after encrypting
    @ColumnInfo(name = "attachment_size")
    private long attachmentSize;

    @ColumnInfo(name = "thumbnail_uri")
    private String thumbnailUri;


    private int content_type;


    private long create_time;

    private long key_version;

    public static final int FILE_NOT_ENCRYPTED = 0;
    public static final int FILE_ENCRYPTED = 5;

    /**
     * ，0，5（5）
     */
    @ColumnInfo(name = "encrypt_level")
    private int fileEncrypted;

    /**
     * 0: send , 1. receive
     */
    private int send_or_receive = -1;

    /**
     * （@）
     */
    @ColumnInfo(name = "ext_content")
    private String extContent;

    @ColumnInfo(name = "identity_iv")
    private String identityIvString;

    @ColumnInfo(name = "data_random")
    private byte[] dataRandom;
    @ColumnInfo(name = "data_hash")
    private String dataHash;
    @ColumnInfo(name = "thumb_random")
    private byte[] thumbRandom;
    @ColumnInfo(name = "thumb_hash")
    private String thumbHash;

    public long getGid() {
        return gid;
    }

    public void setGid(long gid) {
        this.gid = gid;
    }

    public long getMid() {
        return mid;
    }

    public void setMid(long mid) {
        this.mid = mid;
    }

    public String getFrom_uid() {
        return from_uid;
    }

    public void setFrom_uid(String from_uid) {
        this.from_uid = from_uid;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getSend_state() {
        return send_state;
    }

    public void setSend_state(int send_state) {
        this.send_state = send_state;
    }

    public int getRead_state() {
        return read_state;
    }

    public void setRead_state(int read_state) {
        this.read_state = read_state;
    }

    public int getSend_or_receive() {
        return send_or_receive;
    }

    public void setSend_or_receive(int send_or_receive) {
        this.send_or_receive = send_or_receive;
    }

    public int getIs_confirm() {
        return is_confirm;
    }

    public void setIs_confirm(int is_confirm) {
        this.is_confirm = is_confirm;
    }

    public String getAttachment_uri() {
        return attachment_uri;
    }

    public void setAttachment_uri(String attachment_uri) {
        this.attachment_uri = attachment_uri;
    }

    public int getContent_type() {
        return content_type;
    }

    public void setContent_type(int content_type) {
        this.content_type = content_type;
    }

    public long getCreate_time() {
        return create_time;
    }

    public void setCreate_time(long create_time) {
        this.create_time = create_time;
    }

    public int getFileEncrypted() {
        return fileEncrypted;
    }

    public void setFileEncrypted(int fileEncrypted) {
        this.fileEncrypted = fileEncrypted;
    }

    public boolean isFileEncrypted() {
        return fileEncrypted == FILE_ENCRYPTED;
    }

    public void setFileEncrypted(boolean isEncrypted) {
        fileEncrypted = isEncrypted ? FILE_ENCRYPTED : FILE_NOT_ENCRYPTED;
    }

    public String getExtContent() {
        return extContent;
    }

    public void setExtContent(String extContent) {
        this.extContent = extContent;
    }

    public String getIdentityIvString() {
        return identityIvString;
    }

    public void setIdentityIvString(String identityIvString) {
        this.identityIvString = identityIvString;
    }

    public long getKey_version() {
        return key_version;
    }

    public void setKey_version(long key_version) {
        this.key_version = key_version;
    }

    public String getThumbnailUri() {
        return thumbnailUri;
    }

    public void setThumbnailUri(String thumbnailUri) {
        this.thumbnailUri = thumbnailUri;
    }

    public byte[] getDataRandom() {
        return dataRandom;
    }

    public String getDataRandomString() {
        if (dataRandom != null) {
            return Base64.encodeBytes(dataRandom);
        }
        return null;
    }

    public void setDataRandom(byte[] dataRandom) {
        this.dataRandom = dataRandom;
    }

    public String getDataHash() {
        return dataHash;
    }

    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
    }

    public byte[] getThumbRandom() {
        return thumbRandom;
    }

    public String getThumbRandomString() {
        if (thumbRandom != null) {
            return Base64.encodeBytes(thumbRandom);
        }
        return null;
    }

    public void setThumbRandom(byte[] thumbRandom) {
        this.thumbRandom = thumbRandom;
    }

    public String getThumbHash() {
        return thumbHash;
    }

    public void setThumbHash(String thumbHash) {
        this.thumbHash = thumbHash;
    }

    public long getAttachmentSize() {
        return attachmentSize;
    }

    public void setAttachmentSize(long attachmentSize) {
        this.attachmentSize = attachmentSize;
    }
}
