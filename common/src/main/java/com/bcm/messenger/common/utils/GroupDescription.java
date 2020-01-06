package com.bcm.messenger.common.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.groups.GroupUpdateModel;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.recipients.RecipientModifiedListener;
import com.bcm.messenger.utility.Base64;
import com.bcm.messenger.utility.logger.ALog;
import com.google.gson.Gson;

import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * bcm.social.01 2018/9/4.
 */
public class GroupDescription {
    private static final String TAG = "GroupDescription";

    @NonNull
    private final Context context;
    @Nullable
    private final SignalServiceProtos.GroupContext groupContext;
    @Nullable
    private final List<Recipient> members;
    @Nullable
    private GroupUpdateModel groupUpdateModel;

    @Nullable
    public GroupUpdateModel getGroupUpdateModel() {
        return groupUpdateModel;
    }

    public static @NonNull
    GroupDescription getDescription(@NonNull Context context, @Nullable String encodedGroup) {
        if (encodedGroup == null) {
            return new GroupDescription(context, null);
        }

        try {
            SignalServiceProtos.GroupContext groupContext = SignalServiceProtos.GroupContext.parseFrom(Base64.decode(encodedGroup));
            return new GroupDescription(context, groupContext);
        } catch (IOException e) {
            Log.w(TAG, e);
            return new GroupDescription(context, null);
        }
    }

    public GroupDescription(@NonNull Context context, @Nullable SignalServiceProtos.GroupContext groupContext) {
        this.context = context.getApplicationContext();
        this.groupContext = groupContext;

        if (groupContext == null || groupContext.getMembersList().isEmpty()) {
            this.members = null;
            this.groupUpdateModel = null;
        } else {
            this.members = new LinkedList<>();
            try {
                Gson gson = new Gson();
                groupUpdateModel = gson.fromJson(groupContext.getMembers(0), GroupUpdateModel.class);
                for (String member : groupUpdateModel.getNumbers()) {
                    Recipient recipient = Recipient.from(AMELogin.INSTANCE.getMajorContext(), member, true);
                    this.members.add(recipient);
                }
            } catch (Exception ignore) {
                ALog.e(TAG, "GroupDescription init error", ignore);
            }

        }
    }

    @Deprecated
    public String toString(Recipient sender) {
        StringBuilder description = new StringBuilder();
        if (this.groupUpdateModel == null) {
            return description.toString();
        }
        switch (groupUpdateModel.getAction()) {
            case GroupUpdateModel.GROUP_CREATE:
                return handleGroupCreate(groupUpdateModel, description);
            case GroupUpdateModel.GROUP_MEMBER_JOINED:
                return handleGroupMemberJoined(groupUpdateModel, description);
            case GroupUpdateModel.GROUP_AVATAR_CHANGED:
                return handleGroupAvatarChanged(groupUpdateModel, description);
            case GroupUpdateModel.GROUP_TITLE_CHANGED:
                return handleGroupTitleChanged(groupUpdateModel, description);
            case GroupUpdateModel.GROUP_ADD:
                return description.append("group update ").toString();
            case GroupUpdateModel.GROUP_MEMBER_LEFT:
                return description.append("group update ").toString();
            case GroupUpdateModel.GROUP_UPDATE:
                return description.append("group update ").toString();
            case GroupUpdateModel.GROUP_REMOVE:
                return description.append("group update ").toString();
            default:
                return description.append("group update ").toString();

        }
    }

    private String handleGroupTitleChanged(GroupUpdateModel groupUpdateModel, StringBuilder description) {
        return description.append("group name changed to").append(groupUpdateModel.getInfo()).toString();
    }

    private String handleGroupAvatarChanged(GroupUpdateModel groupUpdateModel, StringBuilder description) {
        return description.append("group avatar changed").toString();
    }

    private String handleGroupMemberJoined(GroupUpdateModel groupUpdateModel, StringBuilder description) {
        if (groupUpdateModel.getSender() != null) {
            description.append(groupUpdateModel.getSender());
            description.append(" add ");
        }
        if (groupUpdateModel.getTarget() != null) {
            for (String nikeName : groupUpdateModel.getTarget()) {
                description.append(nikeName);
                description.append(",");
            }
        }
        return description.append(" joined group").toString();
    }

    private String handleGroupCreate(GroupUpdateModel groupUpdateModel, StringBuilder description) {
        if (!TextUtils.isEmpty(groupUpdateModel.getSender())) {
            description.append(groupUpdateModel.getSender());
        }
        description.append(" create group");
        description.append("\n");

        if (groupUpdateModel.getTarget() != null) {
            for (String nikeName : groupUpdateModel.getTarget()) {
                description.append(nikeName);
                description.append(",");
            }
            description.append(" joined  group");
        }

        return description.toString();
    }

    public void addListener(RecipientModifiedListener listener) {
        if (this.members != null) {
            for (Recipient member : this.members) {
                member.addListener(listener);
            }
        }
    }

}
