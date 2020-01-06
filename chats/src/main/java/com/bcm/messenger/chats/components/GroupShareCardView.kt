package com.bcm.messenger.chats.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.provider.GroupModuleImp
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.activity.WebActivity
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.getDrawable
import kotlinx.android.synthetic.main.chats_layout_group_name_card.view.*
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.provider.AMELogin

/**
 * Created by wjh on 2019/6/4
 */
class GroupShareCardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        androidx.constraintlayout.widget.ConstraintLayout(context, attrs, defStyle) {

    private val TAG = "GroupShareCardView"
    private var mShareContent: AmeGroupMessage.GroupShareContent? = null
    private var mUrl: String = ""

    init {
        View.inflate(this.context, R.layout.chats_layout_group_name_card, this)

        setOnClickListener {
            if (mUrl.isNotEmpty()) {
                it.context.startBcmActivity(AMELogin.majorContext, Intent(it.context, WebActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.WEB_URL, mUrl)
                })
            }
        }

        share_action_tv.setOnClickListener {
            // Clicked join group
            val shareContent = mShareContent ?: return@setOnClickListener

            val eKey = shareContent.ekey
            val eKeyByteArray = if (!eKey.isNullOrEmpty()) {
                try {
                    eKey.base64Decode()
                } catch(e:Throwable) {
                    null
                }
            } else {
                null
            }
            GroupModuleImp().doGroupJoin(context, shareContent.groupId, shareContent.groupName, shareContent.groupIcon,
                    shareContent.shareCode, shareContent.shareSignature, shareContent.timestamp, eKeyByteArray) {success ->
                ALog.d(TAG, "do join group success: $success")
            }
        }

        val p = 10.dp2Px()
        setPadding(p, p, p, p)
    }

    fun bind(shareContent: AmeGroupMessage.GroupShareContent, outgoing: Boolean) {
        mShareContent = shareContent
        mUrl = shareContent.shareLink ?: ""

        val name = if (shareContent.groupName.isNullOrEmpty()) {
            context.getString(R.string.common_chats_group_default_name)
        }else {
            shareContent.groupName ?: ""
        }

        val span = SpannableStringBuilder()
        if (!outgoing) {
            setBackgroundResource(R.drawable.chats_share_card_incoming_bg)
            share_arrow_iv.setImageResource(R.drawable.common_right_arrow_icon)
            span.append(StringAppearanceUtil.applyAppearance(name, color = getColor(R.color.common_color_black)))
            span.append("\n")
            span.append(StringAppearanceUtil.applyAppearance(mUrl, color = getColor(R.color.common_content_second_color)))
            share_action_tv.background = getDrawable(R.drawable.chats_group_share_card_incoming_button)
            share_action_tv.setTextColor(getColor(R.color.common_app_primary_color))


        }else {
            setBackgroundResource(R.drawable.chats_share_card_outgoing_bg)
            share_arrow_iv.setImageResource(R.drawable.common_right_arrow_white_icon)
            span.append(StringAppearanceUtil.applyAppearance(name, color = getColor(R.color.common_color_white)))
            span.append("\n")
            span.append(StringAppearanceUtil.applyAppearance(mUrl, color = Color.parseColor("#80FFFFFF")))
            share_action_tv.background = getDrawable(R.drawable.chats_group_share_card_outgoing_button)
            share_action_tv.setTextColor(getColor(R.color.common_color_white))
        }

        share_name_tv.text = span
        val c = context
        if (c is Activity && c.isDestroyed) {
        }else {
            try {
                GlideApp.with(c).load(shareContent.groupIcon)
                        .placeholder(R.drawable.common_group_default_logo)
                        .error(R.drawable.common_group_default_logo)
                        .into(share_logo)
            }catch (ex: Exception) {
                ALog.e(TAG, "bind error", ex)
            }
        }
    }

}