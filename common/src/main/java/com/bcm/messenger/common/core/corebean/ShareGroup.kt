package com.bcm.messenger.common.core.corebean

/**
 * 邀请码状态
 */
enum class BcmShareCodeStatus(val status:Int) {
    VALID_CODE(1),   //有效状态
    INVALID_CODE(2), //邀请码较验失败
    NOT_EXIST(3),    //邀请码不存在
    NET_ERROR(4);     //查询过程中发生网络错误

    companion object {
        private val map = BcmShareCodeStatus.values().associateBy(BcmShareCodeStatus::status)
        fun fromInt(type: Int) = map[type]?: BcmShareCodeStatus.NOT_EXIST
    }
}

/**
 * 用户加群请求状态
 */
enum class BcmGroupJoinStatus(val status:Int) {
    WAIT_OWNER_REVIEW(1), //表示待群主审批
    WAIT_MEMBER_REVIEW(2), //表示不需要群主审批，待秘钥分发
    OWNER_APPROVED(3), //群主已经允许
    OWNER_REJECTED(4);  //群主已经拒绝

    companion object {
        private val map = BcmGroupJoinStatus.values().associateBy(BcmGroupJoinStatus::status)
        fun fromInt(type: Int) = map[type]?: BcmGroupJoinStatus.OWNER_REJECTED
    }
}

/**
 * 用户加群请求数据
 */
data class BcmGroupJoinRequest(val gid: Long,
                               val uid: String,
                               val name:String,
                               val reqId:Long, //请求的本地索引
                               val mid:Long, //请求对应的消息mid
                               val inviter: String?,//邀请者，如果主动申请，则为空
                               var read: Boolean, //true 已读，false 未读
                               val timestamp:Long,
                               var status: BcmGroupJoinStatus,
                               val comment: String) //进群申请备注
{
    fun isWatingAccepted(): Boolean {
        return status != BcmGroupJoinStatus.OWNER_REJECTED && status != BcmGroupJoinStatus.OWNER_APPROVED
    }
}
/**
 * 群主审核进群
 */
data class BcmReviewGroupJoinRequest(val uid: String,
                                     val index:Long, //请求的本地索引
                                     val accepted: Boolean) //accepted true 审核通过, false 拒绝进群
/**
 * 群分享开关配置
 */
data class BcmGroupShareConfig(private val gid:Long,
                               private val shareCode:String, //进群邀请码
                               private val shareSign:String, //进群邀请码签名
                               private val shareEnable:Boolean, //true 群激活了二维码分享功能, false 未激活
                               private val needOwnerConfirm:Boolean)//true 需要群主审批， false 不需要审批

