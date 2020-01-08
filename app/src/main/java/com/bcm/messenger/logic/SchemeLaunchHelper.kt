package com.bcm.messenger.logic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.R
import com.bcm.messenger.chats.privatechat.webrtc.CameraState
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.accountmodule.IAdHocModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.common.provider.accountmodule.IChatModule
import com.bcm.messenger.contacts.FriendRequestsListActivity
import com.bcm.messenger.share.SystemShareActivity
import com.bcm.messenger.ui.HomeActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * Helper when other app called
 * Created by wjh on 2019/7/17
 */
class SchemeLaunchHelper(val context: Context) {
    companion object {
        private const val TAG = "OutLaunchHelper"

        private var mLastOutLaunchIntent: Intent? = null

        /**
         * store launch intent
         */
        fun storeSchemeIntent(intent: Intent?) {
            mLastOutLaunchIntent = intent
        }

        /**
         * Get current launch intent
         */
        fun pullOutLaunchIntent(): Intent? {
            val intent = mLastOutLaunchIntent
            mLastOutLaunchIntent = null
            return intent
        }

        fun hasIntent(): Boolean {
            return mLastOutLaunchIntent != null
        }

        /**
         * scheme data test
         */
        fun hasSchemeData(intent: Intent): Boolean {
            val path = intent.getStringExtra(ARouterConstants.PARAM.PARAM_ROUTE_PATH)
            if (path?.isNotEmpty() == true) {
                return true
            }

            val offline = intent.getStringExtra("bcmdata")
            if (offline?.isNotEmpty() == true) {
                return true
            }

            val action = intent.action
            if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
                return true
            } else {
                val schemeData = intent.data
                return schemeData?.path?.isNotEmpty() == true
            }
        }
    }


    /**
     * Route to destination Activity
     */
    fun route(intent: Intent) {
        try {
            handleTopEvent(intent.getStringExtra(ARouterConstants.PARAM.PARAM_DATA))

            val conversation = intent.getStringExtra(ARouterConstants.PARAM.PARAM_ROUTE_PATH)
            ALog.i(TAG, "route")
            if (!conversation.isNullOrEmpty()) {
                when (conversation) {
                    ARouterConstants.Activity.CHAT_CONVERSATION_PATH -> {
                        ALog.i(TAG, "route path im: $conversation")
                        routeToChat(intent)
                        return
                    }
                }
            }

            val offlineMessage = intent.extras?.get("bcmdata") as? String
            if (offlineMessage?.isNotEmpty() == true) {
                ALog.i(TAG, "route offline message")
                routeToChat(offlineMessage)
                return
            }


            val action = intent.action
            if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
                doForSystemShare(intent)
            } else {
                val schemeData = intent.data ?: return
                ALog.i(TAG, "scheme data: $schemeData, ${schemeData.path}")
                val path = schemeData.path
                when (path) {
                    "/native/appaction/commit_log" -> {
                        BcmRouter.getInstance().get(ARouterConstants.Activity.FEEDBACK).navigation(context)
                    }
                    "/native/appaction/logout" -> {
                        val c = context
                        if (c is AppCompatActivity) {
                            AmeModuleCenter.user(AMELogin.majorContext)?.logoutMenu()
                        }
                    }
                    "/native/addfriend/new_chat_page" -> {
                        doForAddFriend(schemeData)
                    }
                    "/addfriend/new_chat_page" -> {
                        doForAddFriend(schemeData)
                    }
                    "/native/joingroup/new_chat_page" -> {
                        doForGroupJoin(schemeData)
                    }
                    "/joingroup/new_chat_page" -> {
                        doForGroupJoinShortLink(schemeData)
                    }
                    else -> {
                        if (path.startsWith("/h5/")) {
                            doForWeb(schemeData)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "router error", ex)
        }
    }

    private fun handleTopEvent(data: String?) {
        try {
            val current = AmeAppLifecycle.current() ?: return
            if (!data.isNullOrEmpty()) {
                val event = GsonUtils.fromJson<HomeTopEvent>(data, object : TypeToken<HomeTopEvent>() {}.type)
                fun continueAction() {
                    if (event.finishTop) {
                        BcmRouter.getInstance().get(ARouterConstants.Activity.APP_HOME_PATH).navigation(current)
                    }

                    val con = event.chatEvent
                    if (con != null) {
                        val threadId = con.threadId
                        val address = con.address
                        if (threadId <= 0L && address != null && con.createIfNotExist) {
                            ThreadListViewModel.getThreadId(Recipient.from(address, true)) { newThreadId ->
                                BcmRouter.getInstance()
                                        .get(con.path)
                                        .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, address)
                                        .putLong(ARouterConstants.PARAM.PARAM_THREAD, newThreadId)
                                        .putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, con.gid ?: -1L)
                                        .startBcmActivity(address.context(), current)
                            }
                        } else {
                            BcmRouter.getInstance()
                                    .get(con.path)
                                    .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, con.address)
                                    .putLong(ARouterConstants.PARAM.PARAM_THREAD, threadId)
                                    .putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, con.gid
                                            ?: -1L)
                                    .navigation(current)
                        }
                    }

                    val call = event.callEvent
                    if (call != null) {
                        AmeModuleCenter.chat(AMELogin.majorContext)?.startRtcCallService(AppContextHolder.APP_CONTEXT, call.address, CameraState.Direction.NONE.ordinal)
                    }
                }

                val notify = event.notifyEvent
                if (notify != null) {
                    ALog.d(TAG, "receive HomeTopEvent success: ${notify.success}, message: ${notify.message}")
                    if (notify.success) {
                        AmeAppLifecycle.succeed(notify.message, true) {
                            continueAction()
                        }
                    } else {
                        AmeAppLifecycle.failure(notify.message, true) {
                            continueAction()
                        }
                    }
                } else {
                    continueAction()
                }
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "handleTopEvent error", ex)
        }
    }

    private fun doForSystemShare(intent: Intent) {
        ALog.i(TAG, "doForSystemShare")
        intent.component = ComponentName(context, SystemShareActivity::class.java)
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom_fast)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom_fast)
        context.startActivity(intent)
    }

    private fun doForAddFriend(uri: Uri) {
        val uid = uri.getQueryParameter("uid")
        if (uid.isNullOrBlank()) {
            return
        }
        val name = uri.getQueryParameter("name")
        AmeModuleCenter.contact(AMELogin.majorContext)?.openContactDataActivity(AppContextHolder.APP_CONTEXT, Address.from(AMELogin.majorContext, uid), name)
    }

    private fun doForGroupJoin(uri: Uri) {
        ALog.i(TAG, "doForGroupJoin uri: $uri")
        val shareContent = AmeGroupMessage.GroupShareContent.fromBcmSchemeUrl(uri.toString())
        if (shareContent != null) {
            val eKey = shareContent.ekey
            val eKeyByteArray = if (!eKey.isNullOrEmpty()) {
                try {
                    eKey.base64Decode()
                } catch (e: Throwable) {
                    null
                }
            } else {
                null
            }
            AmeModuleCenter.group(AMELogin.majorContext)?.doGroupJoin(context, shareContent.groupId, shareContent.groupName, shareContent.groupIcon,
                    shareContent.shareCode, shareContent.shareSignature, shareContent.timestamp, eKeyByteArray) { success ->
                if (!success) {
                    val homeIntent = Intent(context, HomeActivity::class.java)
                    context.startActivity(homeIntent)
                }
            }
        } else {
            ALog.w(TAG, "doForGroupJoin fail, shareContent is null")
        }

    }

    private fun doForGroupJoinShortLink(uri: Uri) {
        ALog.d(TAG, "doForGroupJoinShortLink uri: $uri")
        doForGroupJoin(uri)
    }

    private fun doForWeb(uri: Uri) {
        val url = uri.getQueryParameter("url")
        BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, url).navigation(context)
    }

    private fun routeToChat(message: String) {
        try {
            ALog.i(TAG, "routeToChat by message")

            val notify = Gson().fromJson(message, AmePushProcess.BcmNotify::class.java)
            val accountContext = checkTargetHashRight(notify.targetHash) ?: return
            notify.contactChat?.uid?.let {
                try {
                    val decryptSource = BCMEncryptUtils.decryptSource(accountContext, it.toByteArray())
                    notify.contactChat?.uid = decryptSource
                } catch (e: Exception) {
                    ALog.e(TAG, "Uid decrypted failed!")
                    return
                }
            }
            if (null != notify) {
                when {
                    notify.contactChat != null -> toChat(accountContext, notify.contactChat)
                    notify.groupChat != null -> toGroup(accountContext, notify.groupChat)
                    notify.friendMsg != null -> toFriendReq(accountContext, notify.contactChat)
                    notify.adhocChat != null -> toAdHoc(accountContext, notify.adhocChat)
                }
            }
        } catch (e: JsonSyntaxException) {
            ALog.e(TAG, e)
        }
    }

    private fun routeToChat(intent: Intent) {
        try {
            ALog.i(TAG, "routeToChat by intent")
            val current = AmeAppLifecycle.current() ?: return

            val address = intent.getParcelableExtra<Address>(ARouterConstants.PARAM.PARAM_ADDRESS)
                    ?: return

            if (address.serialize().length > 100) {
                return
            }

            val thread = intent.getLongExtra(ARouterConstants.PARAM.PARAM_THREAD, -1)
            val data = intent.data

            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.CHAT_CONVERSATION_PATH)
                    .putLong(ARouterConstants.PARAM.PARAM_THREAD, thread)
                    .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, address)
                    .setUri(data)
                    .navigation(current)
        } catch (e: Throwable) {
            ALog.e(TAG, "routeToChat", e)
        }

    }

    private fun toChat(accountContext: AccountContext, data: AmePushProcess.ChatNotifyData?) {
        val current = AmeAppLifecycle.current() ?: return
        val uid = data?.uid
        if (uid != null) {
            val address = Address.from(accountContext, uid)
            ThreadListViewModel.getThreadId(Recipient.from(address, true)) {
                BcmRouter.getInstance().get(ARouterConstants.Activity.CHAT_CONVERSATION_PATH)
                        .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, address)
                        .putLong(ARouterConstants.PARAM.PARAM_THREAD, it)
                        .startBcmActivity(accountContext, current)
            }
        } else {
            ALog.e(TAG, "chat - unknown push data")
        }

    }

    private fun toGroup(accountContext: AccountContext, data: AmePushProcess.GroupNotifyData?) {
        val current = AmeAppLifecycle.current() ?: return
        if (data?.gid != null && data.mid != null) {
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.CHAT_GROUP_CONVERSATION)
                    .putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, data.gid ?: 0L)
                    .putLong(ARouterConstants.PARAM.PARAM_THREAD, -1)
                    .putBoolean(ARouterConstants.PARAM.PRIVATE_CHAT.IS_ARCHIVED_EXTRA, true)
                    .putInt(ARouterConstants.PARAM.PRIVATE_CHAT.DISTRIBUTION_TYPE_EXTRA, ThreadRepo.DistributionTypes.NEW_GROUP)
                    .startBcmActivity(accountContext, current)
        } else {
            ALog.e(TAG, "group - unknown push data")
        }
    }

    private fun toFriendReq(accountContext: AccountContext, data: AmePushProcess.FriendNotifyData?) {
        val current = AmeAppLifecycle.current() ?: return
        AmeAppLifecycle.current()?.startBcmActivity(accountContext, Intent(current, FriendRequestsListActivity::class.java))
    }

    private fun toAdHoc(accountContext: AccountContext, data: AmePushProcess.AdHocNotifyData?) {
        val current = AmeAppLifecycle.current() ?: return
        if (!data?.session.isNullOrEmpty()) {
            val adHocProvider = AmeProvider.get<IAdHocModule>(ARouterConstants.Provider.PROVIDER_AD_HOC)
            if (adHocProvider?.isAdHocMode() == true) {
                BcmRouter.getInstance()
                        .get(ARouterConstants.Activity.ADHOC_CONVERSATION)
                        .putString(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, data?.session)
                        .startBcmActivity(accountContext, current)
            } else {
                ALog.i(TAG, "if not adhoc，ignore")
            }

        } else {
            ALog.w(TAG, "adhoc -- unknown push data")
        }
    }

    /**
     * check offline target hash can route
     */
    private fun checkTargetHashRight(targetHash: Long): AccountContext? {
        val accountContext = AmePushProcess.findAccountContext(targetHash)
        if (accountContext == null) {
            ALog.w(TAG, "checkTargetHashRight fail, find account context null")
            return null
        }
        if (accountContext != AMELogin.majorContext) {
            ALog.w(TAG, "checkTargetHashRight fail, accountContext is not major")
            return null
        }
        return accountContext
    }
}