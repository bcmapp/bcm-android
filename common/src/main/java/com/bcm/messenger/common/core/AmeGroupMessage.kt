package com.bcm.messenger.common.core

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.TextUtils
import androidx.room.Ignore
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.R
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.corebean.HistoryMessageDetail
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.min

/**
 * 
 * Created by bcm.social.01 on 2018/6/13.
 */
class AmeGroupMessage<out T : AmeGroupMessage.Content>(
        var type: Long,
        val content: T) : NotGuard {

    companion object {

        const val DECRYPT_FAIL = -1L
        const val NONSUPPORT = 0L
        const val TEXT = 1L
        const val IMAGE = 2L
        const val FILE = 3L
        const val VIDEO = 4L
        const val AUDIO = 5L
        const val LOCATION = 6L
        const val LINK = 7L
        const val SYSTEM_INFO = 8L
        const val CONTACT = 9L
        const val CHAT_REPLY = 10L
        const val GROUP_SHARE_CARD = 11L
        const val ADHOC_INVITE = 12L

        const val SHARE_CHANNEL = 101L
        const val GROUP_KEY = 102L
        const val NEWSHARE_CHANNEL = 103L

        const val FRIEND = 104L

        const val PIN = 105L

        const val EXCHANGE_PROFILE = 106L

        const val RECEIPT = 107L
        const val GROUP_SHARE_SETTING_REFRESH = 108L

        const val CONTROL_MESSAGE = 1000L
        const val SCREEN_SHOT_MESSAGE = 1001L
        const val CHAT_HISTORY = 1002L
        const val GAME_IDIOM = 1003L

        const val LIVE_MESSAGE = 2000L

        const val MESSAGE_SECURE_NOTICE = 3000L

        fun messageFromJson(text: String): AmeGroupMessage<*> {

            try {
                val msgContent = Gson().fromJson<AmeGroupMessage<Content>>(text, object : TypeToken<AmeGroupMessage<Content>>() {}.type)
                when (msgContent.type) {
                    TEXT -> return Gson().fromJson<AmeGroupMessage<TextContent>>(text, object : TypeToken<AmeGroupMessage<TextContent>>() {}.type)
                    AUDIO -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<AudioContent>>() {}.type)
                    FILE -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<FileContent>>() {}.type)
                    IMAGE -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<ImageContent>>() {}.type)
                    LINK -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<LinkContent>>() {}.type)
                    SYSTEM_INFO -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<SystemContent>>() {}.type)
                    LOCATION -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<LocationContent>>() {}.type)
                    VIDEO -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<VideoContent>>() {}.type)
                    SHARE_CHANNEL -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<ShareChannelContent>>() {}.type)
                    NEWSHARE_CHANNEL -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<NewShareChannelContent>>() {}.type)
                    GROUP_KEY -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<GroupKeyContent>>() {}.type)
                    CONTROL_MESSAGE -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<ControlContent>>() {}.type)
                    CONTACT -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<ContactContent>>() {}.type)
                    FRIEND -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<FriendContent>>() {}.type)
                    SCREEN_SHOT_MESSAGE -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<ScreenshotContent>>() {}.type)
                    CHAT_HISTORY -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<HistoryContent>>() {}.type)
                    CHAT_REPLY -> return ReplyContent.from(text)
                    DECRYPT_FAIL -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<TextContent>>() {}.type)
                    LIVE_MESSAGE -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<LiveContent>>() {}.type)
                    PIN -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<PinContent>>() {}.type)
                    EXCHANGE_PROFILE -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<ExchangeProfileContent>>() {}.type)
                    RECEIPT -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<ReceiptContent>>() {}.type)
                    GROUP_SHARE_CARD -> return Gson().fromJson<AmeGroupMessage<*>>(text, object : TypeToken<AmeGroupMessage<GroupShareContent>>() {}.type)
                    GROUP_SHARE_SETTING_REFRESH -> return Gson().fromJson<AmeGroupMessage<GroupShareSettingRefreshContent>>(text, object : TypeToken<AmeGroupMessage<GroupShareSettingRefreshContent>>() {}.type)
                    ADHOC_INVITE -> return Gson().fromJson<AmeGroupMessage<AirChatContent>>(text, object : TypeToken<AmeGroupMessage<AirChatContent>>() {}.type)

                }
            } catch (ignored: Throwable) {
                try {
                    val exception = Gson().fromJson(text, ExceptionType::class.java)
                    if (exception.type == SCREEN_SHOT_MESSAGE)
                        return AmeGroupMessage(SCREEN_SHOT_MESSAGE, ScreenshotContent(exception.content))
                } catch (ignored: Throwable) {

                }
                return AmeGroupMessage(TEXT, TextContent(text))
            }
            if (text == AppContextHolder.APP_CONTEXT.getString(R.string.common_decrypt_message_error)) {
                return AmeGroupMessage(DECRYPT_FAIL, TextContent(AppContextHolder.APP_CONTEXT.getString(R.string.common_error_decrypting_message_snapshot)))
            }
            return AmeGroupMessage(NONSUPPORT, TextContent(AppContextHolder.APP_CONTEXT.getString(R.string.common_unsupport_type_message_snapshot)))
        }

    }

    fun isText(): Boolean {
        return this.type == TEXT
    }

    fun isLink(): Boolean {
        return this.type == LINK
    }

    fun isAudio(): Boolean {
        return this.type == AUDIO
    }

    fun isImage(): Boolean {
        return this.type == IMAGE
    }

    fun isFile(): Boolean {
        return this.type == FILE
    }

    fun isVideo(): Boolean {
        return this.type == VIDEO
    }

    fun isLocation(): Boolean {
        return this.type == LOCATION
    }

    fun isSystemInfo(): Boolean {
        return this.type == SYSTEM_INFO
    }

    fun isLiveMessage(): Boolean {
        return this.type == LIVE_MESSAGE
    }

    fun isMediaMessage(): Boolean {
        return (this.type == FILE)
                || (this.type == AUDIO)
                || (this.type == VIDEO)
                || (this.type == IMAGE)
    }

    fun isWithThumbnail(): Boolean {
        return (this.type == VIDEO || this.type == IMAGE)
    }

    fun isPin(): Boolean {
        return this.type == PIN
    }

    fun isGroupShare(): Boolean {
        return this.type == GROUP_SHARE_CARD
    }

    fun isContactCard(): Boolean {
        return this.type == CONTACT
    }

    fun isExchangeProfile(): Boolean {
        return this.type == EXCHANGE_PROFILE
    }

    fun isReplyMessage(): Boolean {
        return this.type == CHAT_REPLY
    }

    override fun toString(): String {
        if (type == SCREEN_SHOT_MESSAGE) {
            return Gson().toJson(ExceptionType(SCREEN_SHOT_MESSAGE, (content as ScreenshotContent).name))
        }
        return Gson().toJson(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AmeGroupMessage<*>

        if (type != other.type) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + content.hashCode()
        return result
    }


    open class ThumbnailContent(url: String = "", sign: String = "", size: Long = 0L, var thumbnail_url: String = "", var sign_thumbnail: String = "", mimeType: String = "") : AttachmentContent(url, sign, size, mimeType) {

        /**
         * 
         */
        fun getThumbnailExtension(): String {
            val thumb = if (thumbnail_url.isEmpty()) {
                url
            } else {
                thumbnail_url
            }

            val index = thumb.lastIndexOf(File.separator)
            return if (index != -1) {
                thumb.substring(index + 1)
            } else {
                "${System.currentTimeMillis()}"
            }
        }

        open fun getThumbnailPath(): Pair<String, String> {
            return Pair(AmeFileUploader.ENCRYPT_DIRECTORY, AmeFileUploader.DECRYPT_DIRECTORY)
        }

        open fun isThumbnailExist(): Boolean {
            val path = getThumbnailPath()
            return BcmFileUtils.isExist(path.second + File.separator + getThumbnailExtension())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (!super.equals(other)) return false

            other as ThumbnailContent

            if (thumbnail_url != other.thumbnail_url) return false
            if (sign_thumbnail != other.sign_thumbnail) return false

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + thumbnail_url.hashCode()
            result = 31 * result + sign_thumbnail.hashCode()
            return result
        }


    }

    abstract class AttachmentContent(var url: String = "", var sign: String = "", var size: Long = 0L, var mimeType: String = "") : Content() {
        companion object {//
            const val SIZE_MAX = 64 * 1024 * 1024
        }
        /**
         * 
         */
        fun getExtension(): String {
            val index = url.lastIndexOf(File.separator)
            return if (index > -1) {
                url.substring(index + 1)
            } else {
                "${System.currentTimeMillis()}"
            }
        }

        /**
         * 
         */
        open fun getPath(): Pair<String, String> {
            return Pair(AmeFileUploader.ENCRYPT_DIRECTORY, AmeFileUploader.DECRYPT_DIRECTORY)
        }

        open fun isExist(): Boolean {
            val path = getPath()
            return BcmFileUtils.isExist(path.second + File.separator + getExtension())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AttachmentContent

            if (url != other.url) return false
            if (sign != other.sign) return false
            if (mimeType != other.mimeType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = url.hashCode()
            result = 31 * result + sign.hashCode()
            result = 31 * result + mimeType.hashCode()
            return result
        }


    }

    open class Content : NotGuard {

        protected var mRecipientCallback: RecipientModifiedListener? = null

        override fun toString(): String {
            return Gson().toJson(this)
        }

        open fun getDescribe(gid: Long = 0L, accountContext: AccountContext): CharSequence {
            return ""
        }


        open fun isReady() :Boolean {
            return true
        }

        /**
         * @param callback null，
         */
        open fun setRecipientCallback(accountContext: AccountContext, callback: RecipientModifiedListener?) {
            mRecipientCallback = callback
        }
    }


    class TextContent(val text: String = "") : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return text
        }
    }

    class ImageContent(url: String = "", var width: Int = 0, var height: Int = 0, mimeType: String = "", sign: String = "", thumbnail_url: String = "", sign_thumbnail: String = "", size: Long = 0L) :
            ThumbnailContent(url, sign, size, thumbnail_url, sign_thumbnail, mimeType) {

        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_image_message_description)
        }

    }

    class VideoContent(url: String = "", mimeType: String = "", size: Long = 0L, val duration: Long = 0L, sign: String = "", thumbnail_url: String = "", var thumbnail_width: Int = 0, var thumbnail_height: Int = 0, sign_thumbnail: String = "") :
            ThumbnailContent(url, sign, size, thumbnail_url, sign_thumbnail, mimeType) {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_video_message_description)
        }

        override fun getThumbnailPath(): Pair<String, String> {
            return Pair(AmeFileUploader.VIDEO_DIRECTORY, AmeFileUploader.DECRYPT_DIRECTORY)
        }

    }

    class AudioContent(url: String = "", size: Long = 0L, var duration: Long = 0L, mimeType: String = "", sign: String = "") : AttachmentContent(url, sign, size, mimeType) {

        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_audio_message_description)
        }

        override fun getPath(): Pair<String, String> {
            return Pair(AmeFileUploader.AUDIO_DIRECTORY, AmeFileUploader.DECRYPT_DIRECTORY)
        }

    }

    class FileContent(url: String, var fileName: String?, size: Long = 0L, mimeType: String = "", sign: String = "") : AttachmentContent(url, sign, size, mimeType) {

        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_file_message_description)
        }

        override fun getPath(): Pair<String, String> {
            return Pair(AmeFileUploader.DOCUMENT_DIRECTORY, AmeFileUploader.DECRYPT_DIRECTORY)
        }

        companion object {
            /**
             * drawable
             */
            fun getTypeDrawable(fileName: String, fileType: String?, overrideWidth: Int, textSize: Int, specifiedColor: Int = 0): Drawable {

                val ext = getTypeName(fileName, fileType)
                return IndividualAvatarView.createConvertText(AppContextHolder.APP_CONTEXT.resources, ext, overrideWidth,
                        textSize,
                        if (specifiedColor != 0) specifiedColor else getTypeColor(ext), AppUtil.getColor(AppContextHolder.APP_CONTEXT.resources, R.color.common_color_transparent))
            }

            /**
             * text
             */
            fun getTypeName(fileName: String, fileType: String?): String {
                val index = fileName.lastIndexOf(".") + 1
                return if (index == 0) {
                    val suffix = BcmFileUtils.getSuffixByMime(fileType ?: "*/*")
                    getTypeNameBySuffix(suffix)
                } else {
                    fileName.substring(index, min(index + 3, fileName.length)).toUpperCase(Locale.getDefault())
                }
            }

            fun getTypeNameBySuffix(suffix: String): String {
                return if (suffix.length > 3) {
                    suffix.substring(0, 3).toUpperCase(Locale.getDefault())
                }else {
                    suffix.toUpperCase(Locale.getDefault())
                }
            }

            /**
             * 
             */
            fun getTypeColor(ext: String): Int {
                return when (ext) {
                    "DOC" -> Color.parseColor("#379BFF")
                    "XLS" -> Color.parseColor("#3ED645")
                    "PPT" -> Color.parseColor("#FF6A00")
                    "PDF" -> Color.parseColor("#FF3737")
                    else -> {
                        AppUtil.getColor(AppContextHolder.APP_CONTEXT.resources, R.color.common_content_second_color)
                    }
                }
            }
        }

    }


    class LocationContent(val latitude: Double = 0.0, val longtitude: Double = 0.0, val mapType: Int = 0, val title: String = "", val address: String = "") : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_location_message_description)
        }


        override fun equals(other: Any?): Boolean {
            if (other is LocationContent) {
                return this.longtitude == other.longtitude && this.latitude == other.latitude
            }
            return super.equals(other)
        }

        override fun hashCode(): Int {
            var result = latitude.hashCode()
            result = 31 * result + longtitude.hashCode()
            result = 31 * result + mapType
            result = 31 * result + address.hashCode()
            return result
        }
    }

    class ControlContent(val actionCode: Int = 0, val actionDetail: String = "", val actioner: String = "", val messageId: Long?) : Content() {

        companion object {
            const val ACTION_CLEAR_MESSAGE = 0
            const val ACTION_RECALL_MESSAGE = 2
        }

        private var r: Recipient? = null

        override fun setRecipientCallback(accountContext: AccountContext, callback: RecipientModifiedListener?) {
            if (r == null && !actionDetail.isNullOrEmpty()) {
                r = Recipient.from(accountContext, actionDetail, true)
            } else if (r == null && !actioner.isNullOrEmpty()) {
                r = Recipient.from(accountContext, actioner, true)
            }
            if (mRecipientCallback != null) {
                r?.removeListener(mRecipientCallback)
            }
            mRecipientCallback = callback
            if (callback != null) {
                r?.addListener(mRecipientCallback)
            }
        }

        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            val context = AppContextHolder.APP_CONTEXT
            if (r == null && !actionDetail.isNullOrEmpty()) {
                r = Recipient.from(accountContext, actionDetail, true)
                r?.addListener(mRecipientCallback)
            } else if (r == null && !actioner.isNullOrEmpty()) {
                r = Recipient.from(accountContext, actioner, true)
                r?.addListener(mRecipientCallback)
            }
            val self = try {
                Recipient.major()
            } catch (ex: Exception) {
                null
            }
            return if (actionCode == ACTION_CLEAR_MESSAGE) {
                if (r == self) {
                    context.getString(R.string.common_chats_you_clear_history_tip)
                } else {
                    context.getString(R.string.common_chats_partner_clear_history_tip, r?.name
                            ?: "")
                }
            } else if (actionCode == ACTION_RECALL_MESSAGE) {
                if (r == self) {
                    context.getString(R.string.common_chats_you_recalled_message)
                } else {
                    context.getString(R.string.common_chats_he_recalled_message, r?.name ?: "")
                }
            } else {
                ""
            }
        }

    }

    class ScreenshotContent(val name: String = "") : Content() {
        override fun toString(): String {
            return name
        }

        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_screenshot_message_description)
        }
    }

    /**
     * ，iOSLINK，TEXT，，
     */
    class LinkContent(val url: String = "", val text: String = "") : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return url
        }
    }

    class SystemContent(val tipType: Int = 0, var sender: String = "", var theOperator: List<String> = ArrayList(), var extra: String? = null) : Content() {

        companion object {
            //
            const val TIP_JOIN = 100      //
            const val TIP_UPDATE = 101    //
            const val TIP_KICK = 102      //
            const val TIP_BLOCK = 103     //
            const val TIP_UNBLOCK = 104   //
            const val TIP_RECALL = 105    //
            const val TIP_SUBSCRIBE = 106  //
            const val TIP_UNSUBSCRIBE = 107 //
            const val TIP_GROUP_ILLEGAL = 108   //
            const val TIP_LIVE_END = 109   // 
            const val TIP_CHAT_STRANGER_RESTRICTION = 110 //，
            const val TIP_GROUP_INVITE_STRANGER = 111  //，
            const val TIP_JOIN_GROUP_REQUEST = 112 //(，，)
            const val TIP_GROUP_NAME_UPDATE = 113    //
            const val TIP_DECRYPT_FAIL = 114    //
        }

        private var operatorRecipientList: MutableList<Recipient>? = null
        private var senderRecipient: Recipient? = null
        private var groupMembers: Map<String, AmeGroupMemberInfo>? = null
        private var ready: Boolean = false//
        private var resultTip: CharSequence? = null//description

        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            val resultTip = this.resultTip
            if (null != resultTip) {
                return resultTip
            }

            val tip = getSystemTip(gid, accountContext)
            if (isReady()) {
                this.resultTip = tip
            }

            return tip
        }

        override fun isReady(): Boolean {
            return when(tipType) {
                TIP_JOIN, TIP_SUBSCRIBE, TIP_UNSUBSCRIBE, TIP_GROUP_INVITE_STRANGER, TIP_JOIN_GROUP_REQUEST -> {
                    ready
                }
                else -> true
            }
        }

        override fun setRecipientCallback(accountContext: AccountContext, callback: RecipientModifiedListener?) {
            if (senderRecipient == null) {
                if (!TextUtils.isEmpty(sender)) {
                    senderRecipient = Recipient.from(accountContext, sender, true)
                }
            }
            if (mRecipientCallback != null) {
                ALog.d("SystemContent", "remove last recipient callback")
                senderRecipient?.removeListener(mRecipientCallback)
                for (tr in operatorRecipientList ?: emptyList<Recipient>()) {
                    tr.removeListener(mRecipientCallback)
                }
            }
            mRecipientCallback = callback
            if (callback != null) {
                senderRecipient?.addListener(callback)
                for (tr in operatorRecipientList ?: emptyList<Recipient>()) {
                    tr.addListener(mRecipientCallback)
                }
            }
        }

        private fun getSystemTip(gid:Long, accountContext: AccountContext): CharSequence {

            if (senderRecipient == null) {
                if (!TextUtils.isEmpty(sender)) {
                    senderRecipient = Recipient.from(accountContext, sender, true)
                    senderRecipient?.addListener(mRecipientCallback)
                }
            }

            var ol = this.operatorRecipientList
            if (ol == null) {
                ol = mutableListOf()
                this.operatorRecipientList = ol
            }
            if (ol.isEmpty()) {
                for (theOperator in this.theOperator) {
                    val tr = Recipient.from(accountContext, theOperator, true)
                    tr.addListener(mRecipientCallback)
                    ol.add(tr)
                }
            }

            val context = AppContextHolder.APP_CONTEXT
            return when (this.tipType) {
                TIP_JOIN_GROUP_REQUEST -> {
                    val operators = getViewNameFromOperators(context, accountContext, gid, senderRecipient, ol)
                    if (sender.isNullOrEmpty()) {
                        context.getString(R.string.common_chats_group_join_request_description, operators.second)
                    }else {
                        if (theOperator.size > 1) {
                            context.getString(R.string.common_chats_group_invite_multi_description, operators.first, theOperator.size)
                        }else {
                            context.getString(R.string.common_chats_group_invite_single_description, operators.first, operators.second)
                        }
                    }
                }
                TIP_JOIN -> {
                    val operators = getViewNameFromOperators(context, accountContext, gid, senderRecipient, ol)
                    if (operators.first.isEmpty()) {
                        context.getString(R.string.common_chats_group_join, operators.second)
                    }
                    else if (operators.second.isEmpty() || operators.first == operators.second) {
                        context.getString(R.string.common_chats_group_join, operators.first)
                    } else {
                        context.getString(R.string.common_chats_group_invite, operators.first, operators.second)
                    }
                }
                TIP_SUBSCRIBE -> {
                    val operators = getViewNameFromOperators(context, accountContext, gid, senderRecipient, ol)
                    context.getString(R.string.common_chats_subscribe_group, operators.first)
                }
                TIP_UPDATE -> {
                    val operators = getViewNameFromOperators(context, accountContext, gid, senderRecipient, ol)
                    context.getString(R.string.common_chats_group_owner_change, operators.second)
                }
                TIP_KICK -> {
                    val operators = getViewNameFromOperators(context, accountContext, gid, senderRecipient, ol)
                    var tip = ""
                    if (operators.second.isNotEmpty()) {
                        for (recipient in ol) {
                            if (recipient.isLogin()) {
                                tip = context.getString(R.string.common_chats_group_kick_me, operators.first)
                            }
                        }

                        if (tip.isEmpty()) {
                            tip = context.getString(R.string.common_chats_group_remove_member, operators.first, operators.second)
                        }
                        tip
                    } else {
                        context.getString(R.string.common_chats_leave_group, operators.first)
                    }
                }
                TIP_UNSUBSCRIBE -> {
                    val operators = getViewNameFromOperators(context, accountContext, gid, senderRecipient, ol)
                    if (operators.second.isNotEmpty()) {
                        context.getString(R.string.common_chats_group_remove_subscriber, operators.first, operators.second)
                    } else {
                        context.getString(R.string.common_chats_unsubscribe_group, operators.first)
                    }
                }
                TIP_RECALL -> {
                    if (senderRecipient?.address?.serialize() == accountContext.uid) {
                        context.getString(R.string.common_chats_group_you_recall_message)
                    } else {
                        context.getString(R.string.common_chats_peer_recall_message, senderRecipient?.name ?: "")
                    }
                }
                TIP_BLOCK -> {
                    context.getString(R.string.common_chats_user_block_system_notice, senderRecipient?.name ?: "")
                }
                TIP_UNBLOCK -> {
                    context.getString(R.string.common_chats_user_unblock_system_notice, senderRecipient?.name
                            ?: "")
                }
                TIP_GROUP_ILLEGAL -> {
                    context.getString(R.string.common_illegal_group_notice)
                }
                TIP_LIVE_END -> {
                    context.getString(R.string.common_live_end_tip)
                }
                TIP_CHAT_STRANGER_RESTRICTION -> {
                    val target = if (ol.size <= 0) {
                        null
                    }else {
                        ol[0]
                    }
                    if (target == null) {
                        ""
                    }else {
                        if (target.relationship == RecipientRepo.Relationship.FRIEND) {
                            context.getString(R.string.common_chats_stranger_disturb_notice, target.name)

                        } else {
                            context.getString(R.string.common_chats_stranger_disturb_notice, target.name)
                        }
                    }
                }
                TIP_GROUP_INVITE_STRANGER -> {
                    val operators = getViewNameFromOperators(context, accountContext, gid, null, ol)
                    context.getString(R.string.common_group_invite_stranger_notice, operators.second)
                }
                TIP_GROUP_NAME_UPDATE -> {
                    if (theOperator.isNotEmpty()) {
                        val name = "\"${theOperator[0]}\""
                        context.getString(R.string.common_group_name_update_format, name)
                    } else {
                        ""
                    }
                }
                TIP_DECRYPT_FAIL -> {
                    context.getString(R.string.common_message_decrypted_fail_error, extra?:"")
                }
                else -> {
                    ""
                }
            }
        }

        /**
         * 
         */
        private fun getViewNameFromOperators(context: Context, accountContext: AccountContext, gid: Long, from: Recipient?, operators: MutableList<Recipient>): Pair<String, String> {
            if (groupMembers == null) {
                val uidList = operators.map { it.address.serialize() }.toMutableList()
                if (from != null && from.address.serialize() != "welcome") {
                    uidList.add(from.address.serialize())
                }
                syncMemberInfo(accountContext, gid, uidList)
            }
            var fromInDoneList: Recipient? = null
            var fromViewName = ""
            if (from != null) {
                //doneListfrom
                for (u in operators) {
                    if (from.address == u.address) {
                        fromInDoneList = u
                        break
                    }
                }
                if (fromInDoneList != null) {
                    operators.remove(fromInDoneList)
                }

                fromViewName = groupMemberName(from, groupMembers?.get(from.address.serialize()))
                if (from.address.serialize() == accountContext.uid) {
                    fromViewName = context.getString(R.string.common_chats_system_tip_you)
                } else if (from.address.serialize() == "welcome") {
                    fromViewName = ""
                }
            }

            var doneViewName = ""
            for ((index, u1) in operators.withIndex()) {
                doneViewName += if (u1.address.serialize() != accountContext.uid) {
                    groupMemberName(u1, groupMembers?.get(u1.address.serialize()))
                } else {
                    context.getString(R.string.common_chats_system_tip_you_lower_case)
                }
                if (index < operators.size - 1) {
                    doneViewName += ",  "
                }
            }

            if (fromViewName.isNotEmpty()) {
                //"A"
                if (fromViewName != context.getString(R.string.common_chats_system_tip_you)){
                    fromViewName = "\"$fromViewName\""
                }
            }

            if (doneViewName.isNotEmpty()) {
                //"A, B"
                if (doneViewName != context.getString(R.string.common_chats_system_tip_you_lower_case)){
                    doneViewName = "\"$doneViewName\""
                }
            }

            ALog.d("SystemContent", "getViewNameFromOperators first: $fromViewName, second: $doneViewName")
            return Pair(fromViewName, doneViewName)
        }

        /**
         * 
         */
        private fun groupMemberName(recipient: Recipient, member:AmeGroupMemberInfo?) :String {
            return BcmGroupNameUtil.getGroupMemberName(recipient, member)
        }

        /**
         * 
         */
        private fun syncMemberInfo(accountContext: AccountContext, gid: Long, uidList: List<String>) {
            AmeModuleCenter.group(accountContext)?.getMembers(gid, uidList) {
                ALog.d("SystemContent", "syncMemberInfo ready: $ready")
                val map = HashMap<String, AmeGroupMemberInfo>()
                for (i in it) {
                    map[i.uid] = i
                }
                this.groupMembers = map

                if (ready) {
                    return@getMembers
                }
                ready = true

                var recipient = senderRecipient
                if (recipient == null && operatorRecipientList?.isNotEmpty() == true) {
                    recipient = operatorRecipientList?.get(0)
                }

                if (null != recipient) {
                    mRecipientCallback?.onModified(recipient)
                }
            }
        }
    }

    class ShareChannelContent(val channelKey: String = "", val channel: String = "", val name: String = "", val gid: Long = 0L, val icon: String = "", val intro: String = "") : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_share_channel_message_description)

        }
    }

    class NewShareChannelContent(val channelKey: String = "", val channel: String = "", val name: String = "", val gid: Long = 0L, val icon: String = "", val intro: String = "") : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_share_channel_message_description)
        }
    }

    class GroupKeyContent(val key: String, gid: Long = 0L) : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return ""
        }
    }

    /**
     * 
     */
    class ContactContent(val nickName: String = "", @SerializedName("phoneNumber") val uid: String = "", val url: String = "") : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_contact_message_description)
        }
    }

    /**
     * 
     */
    class GroupShareContent(val groupId: Long, val groupName: String?, val groupIcon: String?, val shareCode: String, val shareSignature: String, val ekey: String?, val timestamp: Long, var shareLink: String?) : Content() {

        companion object {
            private const val SHARE_SHORT_LINK_DEV = "http://47.90.96.58:8082/groups/"
            private const val SHARE_SHORT_LINK = "https://s.bcm.social/groups/"
            private const val SHARE_LINK = "https:///bcm.social/native/joingroup/new_chat_page"
            private const val SHARE_SCHEME_URL = "bcmobile://www.bcm-im.com/native/joingroup/new_chat_page"
            private const val SHARE_SCHEME_NEW_URL = "bcm://scheme.bcm-im.com/joingroup/new_chat_page"

            fun fromJson(jsonString: String): GroupShareContent {
                return GsonUtils.fromJson(jsonString, object : TypeToken<GroupShareContent>(){}.type)
            }

            fun fromClipboard(clipText: String): GroupShareContent? {
                try {
                    val json = JSONObject(clipText)
                    return GroupShareContent(json.optString("gid", "0").toLong(),
                            json.optString("name"), json.optString("icon"),
                            json.optString("code", ""), json.optString("signature", ""),
                            json.optString("ekey"),
                            json.optString("timestamp", "0").toLong(), null)

                } catch (ex: Exception) {

                }
                return null
            }

            /**
             * content
             */
            fun fromLink(linkUrl: String): GroupShareContent? {
                return try {
                    val uri = Uri.parse(linkUrl)
                    val gidParam = uri.getQueryParameter("gid")
                    val gid = gidParam?.toLong() ?: throw Exception("gid is null")
                    val name = uri.getQueryParameter("name")
                    val icon = uri.getQueryParameter("icon")
                    val code = uri.getQueryParameter("code")
                    val signature = uri.getQueryParameter("signature")
                    val ekey = uri.getQueryParameter("ekey")
                    val timestampParam = uri.getQueryParameter("timestamp")
                    val timestamp = timestampParam?.toLong() ?: System.currentTimeMillis()
                    if (linkUrl.startsWith(SHARE_LINK) && gid > 0L && !code.isNullOrEmpty() && !signature.isNullOrEmpty()) {
                        GroupShareContent(gid, name, icon, code, signature, ekey, timestamp, null)
                    }else {
                        null
                    }
                }catch (ex: Exception) {
                    ALog.e("GroupShareContent", "fromUrl error", ex)
                    null
                }
            }

            /**
             * bcmcontent
             */
            fun fromBcmSchemeUrl(bcmUrl: String): GroupShareContent? {
                return try {
                    val uri = Uri.parse(bcmUrl)
                    val gidParam = uri.getQueryParameter("gid")
                    val gid = gidParam?.toLong() ?: throw Exception("gid is null")
                    val name = uri.getQueryParameter("name")
                    val icon = uri.getQueryParameter("icon")
                    val code = uri.getQueryParameter("code")
                    val signature = uri.getQueryParameter("signature")
                    val ekey = uri.getQueryParameter("ekey")
                    val timestampParam = uri.getQueryParameter("timestamp")
                    val timestamp = timestampParam?.toLong() ?: System.currentTimeMillis()
                    if ((bcmUrl.startsWith(SHARE_SCHEME_URL) || bcmUrl.startsWith(SHARE_SCHEME_NEW_URL)) && gid != 0L && !code.isNullOrEmpty() && !signature.isNullOrEmpty()) {
                        GroupShareContent(gid, name, icon, code, signature, ekey, timestamp, null)
                    }else {
                        null
                    }
                }catch (ex: Exception) {
                    ALog.e("GroupShareContent", "fromUrl error", ex)
                    null
                }
            }

            fun toShortLink(index: String, hashBase62: String): String {
                if (!AppUtil.isReleaseBuild() && AppUtil.isTestEnvEnable() && !AppUtil.isLbsEnable()) {
                    return "$SHARE_SHORT_LINK_DEV$index#$hashBase62"
                }
                return "$SHARE_SHORT_LINK$index#$hashBase62"
            }
        }

        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(R.string.common_group_share_message_description)
        }

        fun toOldLink(): String {
            return try {
                // ，
                "$SHARE_LINK?gid=$groupId&name=${URLEncoder.encode(if(groupName.isNullOrEmpty())
                    getString(R.string.common_chats_group_default_name) else groupName, "UTF-8")}&code=${URLEncoder.encode(shareCode, "UTF-8")}" +
                        "&signature=${URLEncoder.encode(shareSignature, "UTF-8")}&ekey=${URLEncoder.encode(ekey, "UTF-8")}&timestamp=$timestamp"
            }catch (ex: Exception) {
                ALog.e("GroupShareContent", "toLink error", ex)
                ""
            }
        }

        fun toBcmSchemeUrl(): String {
            return try {
                "$SHARE_SCHEME_NEW_URL?gid=${groupId}&name=${URLEncoder.encode(if(groupName.isNullOrEmpty())
                    getString(R.string.common_chats_group_default_name) else groupName, "UTF-8")}" +
                        "&code=${URLEncoder.encode(shareCode, "UTF-8")}&signature=${URLEncoder.encode(shareSignature, "UTF-8")}" +
                        "&ekey=${URLEncoder.encode(ekey, "UTF-8")}&timestamp=$timestamp"
            }catch (ex: Exception) {
                ALog.e("GroupShareContent", "toBcmSchemeUrl error", ex)
                ""
            }
        }

        fun toShortJson(): String {
            try {
                val json = JSONObject()
                json.put("gid", groupId)
                json.put("code", shareCode)
                json.put("signature", shareSignature)
                json.put("name", groupName)
                json.put("icon", groupIcon)
                json.put("ekey", ekey)
                return json.toString()
            }catch (ex: Exception) {
                ex.printStackTrace()
            }
            return ""
        }

        override fun toString(): String {
            return GsonUtils.toJson(this)
        }
    }

    /*
     * 
     */
    class HistoryContent(val messageList: List<HistoryMessageDetail> = ArrayList()) : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_history_message_description)
        }
    }

    /**
     * 
     */
    class ReplyContent(@SerializedName("replyMid") val mid: Long, @SerializedName("replyUid") val uid: String,
                       @SerializedName("replyContent") val replyString: String, @SerializedName("replyText") val text: String) : Content() {

        companion object {

            @Throws(Exception::class)
            fun from(groupMessageText: String): AmeGroupMessage<*> {

                val contentJson = JSONObject(groupMessageText).getJSONObject("content")
                val replyUid = contentJson.optString("replyUid", "")
                val replyMid = contentJson.optLong("replyMid", 0)
                val replyText = contentJson.optString("replyText", "")
                val replyContentString = contentJson.optString("replyContent", "")
                return AmeGroupMessage(CHAT_REPLY, ReplyContent(replyMid, replyUid, replyContentString, replyText))
            }
        }

        @Ignore
        private var replyMessage: AmeGroupMessage<*> = messageFromJson(replyString)

        fun getReplyMessage(): AmeGroupMessage<*> {
            return replyMessage
        }

        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return text
        }

        fun getReplyRecipient(accountContext: AccountContext): Recipient? {
            if (uid.isNullOrEmpty()) {
                return null
            }
            return Recipient.from(accountContext, uid, true)
        }

        /**
         * 
         */
        fun getReplyDescribe(gid:Long, accountContext: AccountContext, isOutgoing: Boolean): CharSequence {
            val contentBuilder = SpannableStringBuilder()
            val resource = AppContextHolder.APP_CONTEXT.resources
            val icon = when (replyMessage.type) {
                IMAGE -> {
                    if (isOutgoing) {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_image_sent_icon)
                    } else {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_image_received_icon)
                    }
                }
                VIDEO -> {
                    if (isOutgoing) {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_video_sent_icon)
                    } else {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_video_received_icon)
                    }
                }
                FILE -> {
                    if (isOutgoing) {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_file_sent_icon)

                    } else {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_file_received_icon)
                    }
                }
                AUDIO -> {
                    if (isOutgoing) {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_audio_sent_icon)

                    } else {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_audio_received_icon)

                    }
                }
                LOCATION -> {
                    if (isOutgoing) {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_location_sent_icon)

                    } else {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_location_received_icon)
                    }
                }
                CONTACT -> {
                    if (isOutgoing) {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_namecard_sent_icon)

                    } else {
                        AppUtil.getDrawable(resource, R.drawable.common_chats_reply_namecard_received_icon)
                    }

                }
                else -> {
                    null
                }

            }
            if (icon != null) {
                icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                contentBuilder.append(StringAppearanceUtil.addImage(" ", icon, 0))
                contentBuilder.append(" ")
            }

            if (replyMessage != null && replyMessage.content != null && replyMessage.content.getDescribe(gid, accountContext) != null) {
                contentBuilder.append(replyMessage.content.getDescribe(gid, accountContext).replace(Regex("(\\[|\\])"), ""))
            }
            return contentBuilder
        }
    }

    /**
     * bcm
     */
    class FriendContent(val type: Int = 1, val otherUid: String = "") : Content() {
        companion object {
            const val ADD = 1
            const val ADDED = 2
            const val DELETE = 3
        }

        private var r: Recipient? = null

        override fun setRecipientCallback(accountContext: AccountContext, callback: RecipientModifiedListener?) {
            if (r == null) {
                r = Recipient.from(accountContext, otherUid, true)
            }
            if (mRecipientCallback != null) {
                r?.removeListener(mRecipientCallback)
            }
            mRecipientCallback = callback
            r?.addListener(callback)

        }

        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            val context = AppContextHolder.APP_CONTEXT

            if (r == null) {
                r = Recipient.from(accountContext, otherUid, true)
                r?.addListener(mRecipientCallback)
            }
            return if (type == DELETE) {
                context.getString(R.string.common_chats_message_delete_friend_description, r?.name
                        ?: "")
            } else {
                context.getString(R.string.common_chats_message_add_friend_description, r?.name
                        ?: "")
            }

        }
    }

    class PinContent(val mid: Long = 0L) : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.common_pin_message_description)
        }
    }

    class LiveContent(val id: Long, val action: Int, val actionTime: Long, val sourceUrl: String, val duration: Long, val currentSeekTime: Long, val playSource: PlaySource) : Content() {
        private var r: Recipient? = null

        fun isStartLive(): Boolean {
            return action == 1
        }

        fun isRemoveLive(): Boolean {
            return action == -1
        }

        fun isPauseLive(): Boolean {
            return action == 2
        }

        fun isRestartLive(): Boolean {
            return action == 3
        }

        fun isRemovePlayback():Boolean{
            return action == 4
        }

        override fun setRecipientCallback(accountContext: AccountContext, callback: RecipientModifiedListener?) {
            if (mRecipientCallback != null) {
                r?.removeListener(mRecipientCallback)
            }
            mRecipientCallback = callback
            r?.addListener(mRecipientCallback)
        }

        fun getDescription(accountContext: AccountContext, recipient: Recipient?): CharSequence {
            this.r = recipient
            this.r?.addListener(mRecipientCallback)
            return if (recipient?.address?.serialize() == accountContext.uid) {
                AppContextHolder.APP_CONTEXT.getString(if (isStartLive()) R.string.common_live_start_live_tip_by_you else R.string.common_live_stop_live_tip_by_you)
            } else {
                AppContextHolder.APP_CONTEXT.getString(if (isStartLive()) R.string.common_live_start_live_tip else R.string.common_live_stop_live_tip, recipient?.name
                        ?: "")
            }
        }

        class PlaySource(val type: Int, val url: String) : NotGuard
    }

    class ExchangeProfileContent(val nickName: String = "", val avatar: String = "", val version: Int = 1, val type: Int = CHANGE) : Content() {
        companion object {
            const val REQUEST = 0
            const val RESPONSE = 1
            const val CHANGE = 2
        }
    }

    class ReceiptContent(val messageId: Long) : Content()

    class SecureContent() : Content()

    class GroupShareSettingRefreshContent(val shareCode:String, val shareSetting: String, val shareSettingSign:String, val shareAndOwnerConfirmSign:String, val needConfirm:Int ):Content() {

    }


    class AirChatContent(val name: String, val password: String) : Content() {
        override fun getDescribe(gid: Long, accountContext: AccountContext): CharSequence {
            return AppContextHolder.APP_CONTEXT.getString(R.string.common_adhoc_channel_invite_description)
        }
    }

    class ExceptionType(val type: Long = 0L, val content: String = "") : NotGuard

}