package com.bcm.messenger.adhoc.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.adhoc.logic.AdHocSession
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import kotlin.math.min

/**
 * adhoc avatar view
 * Created by wjh on 2019-08-19
 */
class AdHocSessionAvatar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        FrameLayout(context, attrs, defStyleAttr), RecipientModifiedListener {

    private val TAG = "AdHocSessionAvatar"
    private var mPhotoView: IndividualAvatarView = IndividualAvatarView(context)
    private var mSession: AdHocSession? = null
    private var mRecipient: Recipient? = null
    private var mInnerMargin: Int = 10.dp2Px()

    init {
    }

    fun setSession(session: AdHocSession, callback: ((bitmap: Bitmap?) -> Unit)? = null) {
        initPhotoView()
        mSession = session
        val isValid = session.isValid()
        ALog.i(TAG, "setSession session: ${session.sessionId}, isValid: $isValid")
        when {
            session.isChannel() -> {
                mRecipient = null
                mPhotoView.visibility = View.VISIBLE
                if (isValid) {
                    setBackgroundResource(R.drawable.adhoc_border_layer_bg)
                }else {
                    background = null
                }
                mPhotoView.radius = mInnerMargin.toFloat()
                val channel = AdHocChannelLogic.getChannel(session.cid)
                val resource = getChannelDrawableResource(channel?.channelName ?: session.cid)
                callback?.invoke(BitmapFactory.decodeResource(resources, resource))
                mPhotoView.setPhoto(resource)
            }
            session.isChat() -> {
                mPhotoView.visibility = View.VISIBLE
                if (isValid) {
                    setBackgroundResource(R.drawable.adhoc_border_oval_layer_bg)
                }else {
                    background = null
                }
                mRecipient = session.getChatRecipient()
                mRecipient?.addListener(this)
                mPhotoView.setOval(true)
                mPhotoView.setCallback(object : IndividualAvatarView.RecipientPhotoCallback {
                    override fun onLoaded(recipient: Recipient?, bitmap: Bitmap?, success: Boolean) {
                        if (mRecipient == recipient) {
                            callback?.invoke(bitmap)
                        }
                    }
                })
                mPhotoView.setPhoto(mRecipient)
            }
            else -> {
                mPhotoView.visibility = View.GONE
                mRecipient = null
            }
        }
        mPhotoView.alpha = if (isValid) 1.0f else 0.5f
    }

    fun setChannel(channelName: String, callback: ((bitmap: Bitmap?) -> Unit)?) {
        initPhotoView()
        mSession = null
        mRecipient = null
        mPhotoView.visibility = View.VISIBLE
        setBackgroundResource(R.drawable.adhoc_border_layer_bg)
        mPhotoView.radius = mInnerMargin.toFloat()
        val resource = getChannelDrawableResource(channelName)
        callback?.invoke(BitmapFactory.decodeResource(resources, resource))
        mPhotoView.setPhoto(resource)
        mPhotoView.alpha = 1.0f
    }

    override fun onModified(recipient: Recipient) {
        if (mRecipient == recipient) {
            mPhotoView.setPhoto(recipient)
        }
    }

    private fun initPhotoView() {
        for (i in 0 until childCount) {
            if (getChildAt(i) == mPhotoView) {
                return
            }
        }
        ALog.i("AdHocSessionAvatar", "w: ${layoutParams.width}, h:${layoutParams.height}")
        val m = min(layoutParams.width, layoutParams.height)
        mInnerMargin = m / 8
        addView(mPhotoView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(mInnerMargin, mInnerMargin, mInnerMargin, mInnerMargin)
        })
    }

    private fun getChannelDrawableResource(channelId: String): Int {
        return when ((channelId.hashCode() % 1000000) / 100000) {
            0 -> R.drawable.adhoc_channel_icon_0
            1 -> R.drawable.adhoc_channel_icon_1
            2 -> R.drawable.adhoc_channel_icon_2
            3 -> R.drawable.adhoc_channel_icon_3
            4 -> R.drawable.adhoc_channel_icon_4
            5 -> R.drawable.adhoc_channel_icon_5
            6 -> R.drawable.adhoc_channel_icon_6
            7 -> R.drawable.adhoc_channel_icon_7
            8 -> R.drawable.adhoc_channel_icon_8
            9 -> R.drawable.adhoc_channel_icon_9
            else -> R.drawable.adhoc_channel_icon_0
        }
    }
}