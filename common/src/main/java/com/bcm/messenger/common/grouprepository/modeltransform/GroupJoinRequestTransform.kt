package com.bcm.messenger.common.grouprepository.modeltransform

import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.corebean.BcmGroupJoinRequest
import com.bcm.messenger.common.core.corebean.BcmGroupJoinStatus
import com.bcm.messenger.common.grouprepository.room.entity.GroupJoinRequestInfo
import com.bcm.messenger.common.grouprepository.room.entity.JoinGroupReqComment
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.GsonUtils

object GroupJoinRequestTransform {
    private fun bcmJoinGroupRequestFromDb(dbRequest:GroupJoinRequestInfo): BcmGroupJoinRequest{
        var comment:JoinGroupReqComment? = null;
        try {
            comment = GsonUtils.fromJson<JoinGroupReqComment>(dbRequest.comment, JoinGroupReqComment::class.java)
        } catch (e:Exception) {
            ALog.e("GroupJoinRequestTransform", "wrong json format ${dbRequest.gid}")
        }

        return BcmGroupJoinRequest(dbRequest.gid,
                dbRequest.uid,
                comment?.name?:Address.from(dbRequest.uid).format(),
                dbRequest.reqId,
                dbRequest.mid,
                dbRequest.inviter,
                dbRequest.read == 1,
                dbRequest.timestamp,
                BcmGroupJoinStatus.fromInt(dbRequest.status),
                comment?.comment?:"")
    }

    fun bcmJoinGroupRequestListFromDb(dbRequests:List<GroupJoinRequestInfo>): List<BcmGroupJoinRequest>{
        val list = ArrayList<BcmGroupJoinRequest>()
        for (dbRequest in dbRequests) {
            list.add(bcmJoinGroupRequestFromDb(dbRequest))
        }
        return list
    }
}