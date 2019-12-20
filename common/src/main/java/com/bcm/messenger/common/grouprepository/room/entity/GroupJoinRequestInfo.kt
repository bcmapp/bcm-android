package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bcm.messenger.utility.EncryptUtils

@Entity(tableName = GroupJoinRequestInfo.TABLE_NAME, indices = [Index(value = ["uid", "gid", "mid"], unique = true)])
class GroupJoinRequestInfo(var mid: Long, //每个请求都对应一条消息记录
                           var gid: Long,
                           var uid: String) {
    companion object {
        const val TABLE_NAME = "group_join_requests"

        fun calcIndex(gid:Long, mid:Long, uid:String):Long {
            val buf = StringBuffer()
            val byteArray = buf.append(mid)
                    .append(gid)
                    .append(uid)
                    .toString()
                    .toByteArray()

            return EncryptUtils.byteArrayToLong(byteArray)
        }
    }

    @PrimaryKey
    @ColumnInfo(name="req_id")
    var reqId: Long = 0
    @ColumnInfo(name = "identity_key")
    var uidIdentityKey = ""
    var inviter: String = ""//邀请者，如果主动申请，则为空
    @ColumnInfo(name = "inviter_identity_key")
    var inviterIdentityKey = ""
    var read: Int = 0 //1 已读，0 未读
    var timestamp: Long = 0L
    var status: Int = 0
    var comment: String = ""

    init {
        if (mid != 0L) {
            reqId = calcIndex(gid, mid, uid)
        }
    }

    fun updateMid(mid:Long) {
        this.mid = mid
        updateReqId()
    }

    private fun updateReqId() {
        if (mid != 0L) {
            reqId = calcIndex(gid, mid, uid)
        }
    }
}