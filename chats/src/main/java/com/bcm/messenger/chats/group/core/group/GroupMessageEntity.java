package com.bcm.messenger.chats.group.core.group;

import android.text.TextUtils;

import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo;
import com.bcm.messenger.utility.Base64;
import com.bcm.messenger.utility.EncryptUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;
import com.google.gson.annotations.SerializedName;

import org.json.JSONObject;
import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.DjbECPublicKey;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ling created in 2018/5/23
 **/
public class GroupMessageEntity implements NotGuard {

    public final static int STATUS_UNKNOWN = 0;
    public final static int STATUS_NORMAL = 1;
    public final static int STATUS_RECALLED = 2;

    @SerializedName("mid")
    public long mid;

    @SerializedName("from_uid")
    private String fromUid;

    @SerializedName("text")
    public String text;

    // 1.chat；2.subscription；3.group update；4.member update；6.recall
    @SerializedName("type")
    public int type;

    // STATUS_UNKNOWN, STATUS_NORMAL, STATUS_RECALLED
    @SerializedName("status")
    public int status;

    @SerializedName("create_time")
    public long createTime;

    @SerializedName("at_list")
    public List<String> atList;

    @SerializedName("at_all")
    public Boolean atAll;

    @SerializedName("source_extra")
    private String sourceExtra;


    public String getFinalSource(GroupInfo groupInfo) throws DecryptSourceException {
        if (TextUtils.isEmpty(sourceExtra)) {
            return fromUid;
        }
        try {
            String sourceExtraDecoded = new String(Base64.decode(sourceExtra), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(sourceExtraDecoded);
            ALog.d("GroupMessageEntity", "getFinalSource source_extra: " + sourceExtraDecoded);
            String encryptSource = json.optString("source");
            String ephemeralPubKey = json.optString("ephemeralPubkey");
            String groupMsgPubKey = json.optString("groupMsgPubkey");
            String iv = json.optString("iv");
            int version = json.optInt("version");
            if (groupInfo == null) {
                throw new Exception("groupInfo is null");
            }
            if (!TextUtils.equals(Base64.encodeBytes(groupInfo.getChannelPublicKey()), groupMsgPubKey)) {
                throw new Exception("groupMsgPubKey is wrong");
            }
            DjbECPublicKey djbECPublicKey = (DjbECPublicKey) Curve.decodePoint(Base64.decode(ephemeralPubKey), 0);
            byte[] ecdh = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(djbECPublicKey.getPublicKey(), groupInfo.getChannelPrivateKey());
            return new String(EncryptUtils.decryptAES(Base64.decode(encryptSource),
                    EncryptUtils.computeSHA256(ecdh), EncryptUtils.MODE_AES, Base64.decode(iv)), StandardCharsets.UTF_8);


        }catch (Exception ex) {
            ALog.e("GroupMessageEntity", "getFinalSource error", ex);
            throw new DecryptSourceException(ex.getCause());
        }
    }

    public static final class DecryptSourceException extends Exception {
        public DecryptSourceException(Throwable cause) {
            super(cause);
        }
    }
}
