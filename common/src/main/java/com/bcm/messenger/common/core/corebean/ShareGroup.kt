package com.bcm.messenger.common.core.corebean

/**
 * 
 */
enum class BcmShareCodeStatus(val status:Int) {
    VALID_CODE(1),   //
    INVALID_CODE(2), //
    NOT_EXIST(3),    //
    NET_ERROR(4);     //

    companion object {
        private val map = BcmShareCodeStatus.values().associateBy(BcmShareCodeStatus::status)
        fun fromInt(type: Int) = map[type]?: BcmShareCodeStatus.NOT_EXIST
    }
}

/**
 * 
 */
enum class BcmGroupJoinStatus(val status:Int) {
    WAIT_OWNER_REVIEW(1), //
    WAIT_MEMBER_REVIEW(2), //，
    OWNER_APPROVED(3), //
    OWNER_REJECTED(4);  //

    companion object {
        private val map = BcmGroupJoinStatus.values().associateBy(BcmGroupJoinStatus::status)
        fun fromInt(type: Int) = map[type]?: BcmGroupJoinStatus.OWNER_REJECTED
    }
}

/**
 * 
 */
data class BcmGroupJoinRequest(val gid: Long,
                               val uid: String,
                               val name:String,
                               val reqId:Long, //
                               val mid:Long, //mid
                               val inviter: String?,//，，
                               var read: Boolean, //true ，false 
                               val timestamp:Long,
                               var status: BcmGroupJoinStatus,
                               val comment: String) //
{
    fun isWatingAccepted(): Boolean {
        return status != BcmGroupJoinStatus.OWNER_REJECTED && status != BcmGroupJoinStatus.OWNER_APPROVED
    }
}
/**
 * 
 */
data class BcmReviewGroupJoinRequest(val uid: String,
                                     val index:Long, //
                                     val accepted: Boolean) //accepted true , false 
/**
 * 
 */
data class BcmGroupShareConfig(private val gid:Long,
                               private val shareCode:String, //
                               private val shareSign:String, //
                               private val shareEnable:Boolean, //true , false 
                               private val needOwnerConfirm:Boolean)//true ， false 

