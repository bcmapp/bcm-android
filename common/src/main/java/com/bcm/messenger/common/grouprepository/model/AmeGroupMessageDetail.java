package com.bcm.messenger.common.grouprepository.model;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.core.AmeGroupMessage;
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager;
import com.bcm.messenger.common.mms.PartAuthority;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.utils.BcmFileUtils;
import com.bcm.messenger.common.utils.MediaUtil;
import com.bcm.messenger.common.crypto.encrypt.FileInfo;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.GsonUtils;
import com.bcm.messenger.utility.dispatcher.AmeDispatcher;
import com.bcm.messenger.utility.logger.ALog;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.whispersystems.signalservice.internal.websocket.GroupMessageProtos;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import kotlin.Unit;


public class AmeGroupMessageDetail {
    private static final String TAG = AmeGroupMessageDetail.class.getSimpleName();

    public enum SendState {
        SEND_SUCCESS(1),
        SENDING(2),
        RECEIVE_SUCCESS(3),
        SEND_FAILED(10000);

        private final long value;

        SendState(long value) {
            this.value = value;
        }

        public long getValue() {
            return value;
        }
    }

    private Long gid;
    private Long serverIndex = 0L;//，0，mid
    private long indexId;//
    private String senderId;

    private long sendTime;
    private SendState sendState;
    private boolean sendByMe = false;
    private long threadId = -1;
    private String attachmentUri;
    private long attachmentSize;
    private Uri thumbnailUri;
    private AmeGroupMessage message;
    private Integer type = 0;

    private long keyVersion = 0;

    private boolean isRead = false;//
    /**
     * 
     */
    private @Nullable
    String extContentString;

    private @Nullable
    ExtensionContent extContent;

    private @Nullable
    List<Recipient> atRecipientList;

    private String identityIvString;

    private boolean isFileEncrypted;

    //FIXME ，
    private boolean isAttachmentDownloading = false;

    private String dataHash;
    private byte[] dataRandom;
    private String thumbHash;
    private byte[] thumbRandom;

    public AmeGroupMessageDetail() {
        this.sendState = SendState.SEND_SUCCESS;
    }

    public AmeGroupMessageDetail(SendState sendState) {
        this.sendState = sendState;
    }

    public boolean isAttachmentDownloading() {
        return isAttachmentDownloading;
    }

    public void setAttachmentDownloading(boolean attachmentDownloading) {
        isAttachmentDownloading = attachmentDownloading;
    }

    //FIXME ，
    private boolean isThumbnailDownloading = false;

    public boolean isThumbnailDownloading() {
        return isThumbnailDownloading;
    }

    public void setThumbnailDownloading(boolean thumbnailDownloading) {
        isThumbnailDownloading = thumbnailDownloading;
    }

    private int isLabel = LABEL_NONE;  //0，1，2
    public static final int LABEL_NONE = 0;
    public static final int LABEL_REPLY = 1;
    public static final int LABEL_PIN = 2;

    public int isLabel() {
        return isLabel;
    }

    public void setLabel(int label) {
        isLabel = label;
    }

    public Long getGid() {
        return gid;
    }

    public void setGid(Long gid) {
        this.gid = gid;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public SendState getSendState() {
        return sendState;
    }

    public void setSendState(SendState sendState) {
        this.sendState = sendState;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public String getAttachmentUri() {
        return attachmentUri;
    }

    public void setAttachmentUri(String attachmentUri) {
        this.attachmentUri = attachmentUri;
    }

    public boolean isSendByMe() {
        return sendByMe;
    }

    public void setSendByMe(boolean sendByMe) {
        this.sendByMe = sendByMe;
    }

    public boolean isSendSuccess() {
        return sendState == SendState.SEND_SUCCESS;
    }

    public boolean isReceiveSuccess() {
        return sendState == SendState.RECEIVE_SUCCESS;
    }

    public boolean isSendFail() {
        return sendState == SendState.SEND_FAILED;
    }

    public boolean isSending() {
        return sendState == SendState.SENDING;
    }

    public long getIndexId() {
        return indexId;
    }

    public void setIndexId(long indexId) {
        this.indexId = indexId;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public AmeGroupMessage getMessage() {
        return message;
    }

    public long getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(long keyVersion) {
        this.keyVersion = keyVersion;
    }

    public @Nullable
    Recipient getSender() {
        if (TextUtils.isEmpty(senderId)) {
            return null;
        } else {
            return Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(senderId), true);
        }
    }

    public boolean isFileEncrypted() {
        return isFileEncrypted;
    }

    public void setFileEncrypted(boolean fileEncrypted) {
        isFileEncrypted = fileEncrypted;
    }

    public Uri getThumbnailUri() {
        return thumbnailUri;
    }

    public void setThumbnailUri(Uri thumbnailUri) {
        this.thumbnailUri = thumbnailUri;
    }

    public @Nullable Uri getFilePartUri() {
        if (attachmentUri == null) {
            return null;
        } else if (!isFileEncrypted) {
            return Uri.parse(attachmentUri);
        } else {
            return PartAuthority.getGroupAttachmentUri(gid, indexId);
        }
    }

    public @Nullable Uri getThumbnailPartUri() {
        if (message.getContent() instanceof AmeGroupMessage.ThumbnailContent && MediaUtil.isGif(((AmeGroupMessage.ThumbnailContent) message.getContent()).getMimeType())) {
            return getFilePartUri();
        } else if (thumbnailUri == null) {
            // Check thumbnail file and save uri to database. Thumbnail had not saved to DB in previous versions.
            // Since 2.4.0
            if (message.getContent() instanceof AmeGroupMessage.ThumbnailContent && ((AmeGroupMessage.ThumbnailContent) message.getContent()).isThumbnailExist()) {
                File file = new File(((AmeGroupMessage.ThumbnailContent) message.getContent()).getThumbnailPath().getSecond() + File.separator + ((AmeGroupMessage.ThumbnailContent) message.getContent()).getThumbnailExtension());

                AmeDispatcher.INSTANCE.getIo().dispatch(() -> {
                    MessageDataManager.INSTANCE.updateMessageThumbnailUri(gid, indexId, new FileInfo(file, file.length(), null, null));
                    return Unit.INSTANCE;
                });

                return Uri.fromFile(file);
            }

            return null;
        }
        return PartAuthority.getGroupThumbnailUri(gid, indexId);
    }

    public String getDataHash() {
        return dataHash;
    }

    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
    }

    public byte[] getDataRandom() {
        return dataRandom;
    }

    public void setDataRandom(byte[] dataRandom) {
        this.dataRandom = dataRandom;
    }

    public String getThumbHash() {
        return thumbHash;
    }

    public void setThumbHash(String thumbHash) {
        this.thumbHash = thumbHash;
    }

    public byte[] getThumbRandom() {
        return thumbRandom;
    }

    public void setThumbRandom(byte[] thumbRandom) {
        this.thumbRandom = thumbRandom;
    }

    public long getAttachmentSize() {
        return attachmentSize;
    }

    public void setAttachmentSize(long attachmentSize) {
        this.attachmentSize = attachmentSize;
    }

    /**
     * 
     *
     * @return
     */
    public boolean isAttachmentComplete() {
        boolean exist = false;
        try {
            if (!TextUtils.isEmpty(attachmentUri)) {
                Uri uri = Uri.parse(attachmentUri);
                if (uri.getScheme().equalsIgnoreCase("content")) {
                    exist = true;
                } else if (uri.getScheme().equalsIgnoreCase("file")) {
                    String path = uri.getPath();
                    exist = BcmFileUtils.INSTANCE.isExist(path);
                }
            }
        } catch (Exception ex) {
            ALog.e("AmeGroupMessageDetail", "isAttachmentComplete error", ex);
            exist = false;
        }
        return exist;
    }

    /**
     * attachmentUri stringuri
     *
     * @return
     */
    public @Nullable
    Uri toAttachmentUri() {
        try {
            if (isAttachmentComplete()) {
                if (attachmentUri.startsWith("/data")) {
                    return Uri.fromFile(new File(attachmentUri));
                } else {
                    return Uri.parse(attachmentUri);
                }
            }
        } catch (Exception ex) {
            ALog.e("AmeGroupMessageDetail", "toAttachmentUri error", ex);
        }
        return null;
    }

    //:，
    public boolean isForwardable() {
        if (message == null)
            return false;

        //Fixme 
        if (message.getType() == AmeGroupMessage.CHAT_HISTORY) {
            return false;
        }

        if (message.getType() == AmeGroupMessage.CONTACT) {
            return false;
        }

        //FIXME:  image ，
        if (message.isImage()) {
            return true;
        } else {
            boolean exist = isAttachmentComplete();
            return !message.isAudio()
                    && (!message.isMediaMessage() || exist);
        }

    }

    // check copable
    public boolean isCopyable() {
        return message != null && (message.isText() || message.isLink() || message.isGroupShare() || message.isReplyMessage());
    }

    // check recallable
    public boolean isRecallable() {
        return message != null && isSendByMe() && isSendSuccess();
    }

    // check reeditable
    public boolean isReeditable() {
        return message != null && message.isText();
    }

    public void setMessage(AmeGroupMessage message) {
        this.message = message;
    }

    public Long getServerIndex() {
        return serverIndex;
    }

    public void setServerIndex(Long serverIndex) {
        this.serverIndex = serverIndex;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public @Nullable
    String getExtContentString() {
        return extContentString;
    }

    @Nullable
    public List<Recipient> getAtRecipientList() {
        return atRecipientList;
    }

    public void setAtRecipientList(@Nullable List<Recipient> atRecipientList) {
        this.atRecipientList = atRecipientList;
    }

    public String getIdentityIvString() {
        return identityIvString;
    }

    public void setIdentityIvString(String identityIvString) {
        this.identityIvString = identityIvString;
    }

    public void setExtContentString(@Nullable String extContentString) {
        if (TextUtils.equals(this.extContentString, extContentString)) {
            return;
        }
        this.extContentString = extContentString;
        ALog.d("AmeGroupMessageDetail", "setExtContentString: " + extContentString);
        try {
            ExtensionContent extContent = extContentString == null ? null : GsonUtils.INSTANCE.fromJson(extContentString, ExtensionContent.class);
            setExtContent(extContent);

        } catch (Exception ex) {
            ALog.e("AmeGroupMessageDetail", "setExtContentString error", ex);
        }
    }

    public @Nullable
    ExtensionContent getExtContent() {
        return extContent;
    }

    public void setExtContent(@Nullable ExtensionContent extContent) {
        if (this.extContent == null && extContent == null || (this.extContent != null && this.extContent.equals(extContent))) {
            return;
        }
        this.extContent = extContent;

        try {
            setExtContentString(extContent == null ? null : new Gson().toJson(extContent, new TypeToken<ExtensionContent>() {
            }.getType()));

        } catch (Exception ex) {
            ALog.e("AmeGroupMessageDetail", "setExtContent error", ex);
        }
        checkAtRecipientList();
    }

    /**
     * @recipient，profile
     */
    private void checkAtRecipientList() {
        List<String> atList = this.extContent == null ? null : this.extContent.getAtList();
        if ((atList != null && !atList.isEmpty()) && (atRecipientList == null || atRecipientList.isEmpty())) {
            List<Recipient> result = new ArrayList<>(atList.size());
            for (String uid : atList) {
                Recipient recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(uid), false);
                result.add(recipient);
            }
            atRecipientList = result;
        }
    }

    @Override
    public String toString() {
        return "AmeGroupMessageDetail{" +
                "gid=" + gid +
                ", serverIndex=" + serverIndex +
                ", indexId=" + indexId +
                ", senderId='" + senderId + '\'' +
                ", sendTime=" + sendTime +
                ", isSend=" + sendState +
                ", sendByMe=" + sendByMe +
                ", threadId=" + threadId +
                ", attachmentUri='" + attachmentUri + '\'' +
                ", message=" + message +
                ", type=" + type +
                ", isRead=" + isRead +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AmeGroupMessageDetail that = (AmeGroupMessageDetail) o;
        return indexId == that.indexId &&
                Objects.equals(gid, that.gid);
    }

    @Override
    public int hashCode() {

        return Objects.hash(gid, indexId);
    }

    /**
     * 
     */
    public static class ExtensionContent {

        @SerializedName("at_list")
        private @Nullable
        List<String> atList;

        @SerializedName("at_all")
        private int atAll;

        public ExtensionContent() {

        }

        public ExtensionContent(@Nullable GroupMessageProtos.GroupChatMsg.ExtensionContent extProtos) {
            if (extProtos != null) {
                atAll = extProtos.getAtAll() ? 1 : 0;
                if (extProtos.getAtListList() != null) {
                    atList = new ArrayList<>(extProtos.getAtListCount());
                    atList.addAll(extProtos.getAtListList());
                }
            }
        }

        @Nullable
        public List<String> getAtList() {
            return atList;
        }

        public void setAtList(@Nullable List<String> atList) {
            this.atList = atList;
        }

        public int getAtAll() {
            return atAll;
        }

        public void setAtAll(int atAll) {
            this.atAll = atAll;
        }

        public boolean isAtAll() {
            return this.atAll == 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ExtensionContent that = (ExtensionContent) o;
            return atAll == that.atAll &&
                    Objects.equals(atList, that.atList);
        }

        @Override
        public int hashCode() {

            return Objects.hash(atList, atAll);
        }
    }
}
