package org.whispersystems.signalservice.api.messages;

import org.whispersystems.signalservice.internal.util.Base64;
import org.whispersystems.signalservice.internal.websocket.FriendProtos;

import java.io.IOException;
import java.util.List;

public class FriendMessageEnvelop {
    private static final String TAG = FriendMessageEnvelop.class.getSimpleName();

    private final FriendProtos.FriendMessage friendMessage;

    public FriendMessageEnvelop(String message) throws IOException {
        this(Base64.decode(message));
    }

    public FriendMessageEnvelop(byte[] textBytes) throws IOException {
        this.friendMessage = FriendProtos.FriendMessage.parseFrom(textBytes);
    }

    public FriendMessageEnvelop(List<FriendProtos.FriendRequest> requests, List<FriendProtos.FriendReply> replies, List<FriendProtos.DeleteFriend> deleteFriends) {
        FriendProtos.FriendMessage.Builder builder = FriendProtos.FriendMessage.newBuilder();
        if (requests != null) {
            builder.addAllRequests(requests);
        }
        if (replies != null) {
            builder.addAllReplies(replies);
        }
        if (deleteFriends != null) {
            builder.addAllDeletes(deleteFriends);
        }
        this.friendMessage = builder.build();
    }

    public List<FriendProtos.FriendRequest> getRequestsList() {
        return friendMessage.getRequestsList();
    }

    public List<FriendProtos.FriendReply> getRepliesList() {
        return friendMessage.getRepliesList();
    }

    public List<FriendProtos.DeleteFriend> getDeletesList() {
        return friendMessage.getDeletesList();
    }
}
