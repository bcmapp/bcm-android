package com.bcm.messenger.contacts.logic

import com.bcm.messenger.common.server.IServerDataListener
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.google.protobuf.AbstractMessage
import org.whispersystems.signalservice.internal.websocket.FriendProtos

class ContactRequestReceiver : IServerDataListener {
    private val TAG = "ContactRequestReceiver"

    override fun onReceiveData(proto: AbstractMessage): Boolean {
        if (proto is FriendProtos.FriendMessage) {
            AmeDispatcher.io.dispatch {
                val deletesList = proto.deletesList
                if (deletesList.isNotEmpty()) {
                    ALog.i(TAG, "Handle delete list ${deletesList?.size}")
                    deletesList.forEach { deleteFriend ->
                        BcmContactLogic.handleDeleteFriend(deleteFriend)
                    }
                }

                val requestsList = proto.requestsList
                if (requestsList.isNotEmpty()) {
                    ALog.i(TAG, "Handle request list ${requestsList?.size}")
                    requestsList.forEach { request ->
                        BcmContactLogic.handleAddFriendRequest(request)
                    }
                }

                val repliesList = proto.repliesList
                if (repliesList.isNotEmpty()) {
                    ALog.i(TAG, "Handle reply list ${repliesList?.size}")
                    repliesList.forEach { reply ->
                        BcmContactLogic.handleFriendReply(reply)
                    }
                }
            }
            return true
        }
        return false
    }
}