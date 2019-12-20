package com.bcm.messenger.common.provider

import android.content.Context
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.recipients.Recipient

/**
 * 联系人公用对外接口
 * Created by ling on 2018/3/14.
 */
interface IContactModule : IAmeModule {

    companion object {
        const val TAG = "IContactProvider"
    }

    /**
     * 清理最近搜索记录等之类的数据
     */
    fun clear()

    /**
     * 打开搜索界面
     */
    fun openSearch(context: Context)

    /**
     * 打开联系人资料卡
     */
    fun openContactDataActivity(context: Context, address: Address, nick: String? = null)

    /**
     * 打开联系人资料卡
     */
    fun openContactDataActivity(context: Context, address: Address, fromGroup: Long)

    /**
     * 打开联系人资料卡
     */
    fun openContactDataActivity(context: Context, address: Address, nick: String?, fromGroup: Long)

    /**
     * 识别二维码(callback result: true标示能识别出bcm可认知的数据，false表示bcm不可认知的数据)
     */
    fun discernScanData(context: Context, qrCode: String, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * 识别链接（callback result: true标示能识别出bcm可认知的数据，false表示bcm不可认知的数据）
     */
    fun discernLink(context: Context, link: String, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * 屏蔽/取消屏蔽 联系人（不需要起子线程，内部已经异步执行）
     */
    fun blockContact(address: Address, block: Boolean, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * 屏蔽/取消屏蔽 联系人（不需要起子线程，内部已经异步执行）
     */
    fun blockContact(addressList: List<Address>, block: Boolean, callback: ((successList: List<Address>) -> Unit)? = null)

    /**
     * 登进时调用
     */
    fun doForLogin()
    /**
     * 登出时调用
     */
    fun doForLogOut()

    /**
     * 添加好友
     */
    fun addFriend(targetUid: String, memo: String, handleBackground: Boolean, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * 主动删除好友
     */
    fun deleteFriend(targetUid: String, callback: ((result: Boolean) -> Unit)? = null)

    fun handleFriendPropertyChanged(targetUid: String, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * 检测是否需要发送后台好友请求
     */
    fun checkNeedRequestAddFriend(context: Context, recipient: Recipient)

    /**
     * 更新会话成员来源
     */
    fun updateThreadRecipientSource(threadRecipientList: List<Recipient>)
}