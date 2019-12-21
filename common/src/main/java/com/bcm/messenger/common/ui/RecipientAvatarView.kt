package com.bcm.messenger.common.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.provider.IGroupModule
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getDrawable
import com.bcm.route.api.BcmRouter
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.common_group_avatar_view.view.*
import com.bcm.messenger.common.R
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import java.io.File

/**
 * 
 *
 * Created by Kin on 2019/6/10
 */
class RecipientAvatarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr), RecipientModifiedListener {
    private val TAG = "RecipientAvatarView"

    private var privateRecipient: Recipient? = null
    private var groupRecipient: Recipient? = null

    private var glide = GlideApp.with(AppContextHolder.APP_CONTEXT)

    private var loadCallback: ((bitmap: Bitmap?) -> Unit)? = null

    init {
        inflate(context, R.layout.common_group_avatar_view, this)

        member_single_avatar.setCallback(object : IndividualAvatarView.RecipientPhotoCallback {
            override fun onLoaded(recipient: Recipient?, bitmap: Bitmap?, success: Boolean) {
                if (success) {
                    loadCallback?.invoke(bitmap)
                }
            }
        })
    }

    override fun onDetachedFromWindow() {
        loadCallback = null
        super.onDetachedFromWindow()
    }

    
    fun showRecipientAvatar(recipient: Recipient) {
        if (recipient.isGroupRecipient) {
            showGroupAvatar(recipient.groupId)
        }else {
            showPrivateAvatar(recipient)
        }
    }

   
    fun showGroupAvatar(gid: Long, showSplice: Boolean = true, path: String = "") {
        ALog.i(TAG, "Show group avatar")

        val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_GROUP_BASE).navigationWithCast<IGroupModule>()
        val groupInfo = provider.getGroupInfo(gid)
        when {
            !groupInfo?.iconUrl.isNullOrBlank() -> {
                ALog.i(TAG, "Group icon url is not empty")
                showGroupAvatar(Recipient.recipientFromNewGroupIdAsync(AppContextHolder.APP_CONTEXT, gid))
            }
            showSplice -> {
                ALog.i(TAG, "Have temp splice avatar")
                when {
                    path.isNotEmpty() -> setCacheAvatar(path)
                    !groupInfo?.spliceAvatarPath.isNullOrEmpty() -> setCacheAvatar(groupInfo?.spliceAvatarPath!!)
                    else -> {
                        setImageResource(R.drawable.common_group_default_logo)
                    }
                }
            }
            else -> {
                setImageResource(R.drawable.common_group_default_logo)
            }
        }

        if ((groupInfo?.name.isNullOrEmpty() && groupInfo?.spliceName.isNullOrEmpty()) || (groupInfo?.iconUrl.isNullOrEmpty() && groupInfo?.spliceAvatarPath.isNullOrEmpty())) {
            provider.refreshGroupNameAndAvatar(gid)
        }
    }

    fun showPrivateAvatar(recipient: Recipient) {
        this.privateRecipient?.removeListener(this)
        this.privateRecipient = recipient
        this.privateRecipient?.addListener(this)

        setRecipientAvatar()
    }

    private fun showGroupAvatar(recipient: Recipient) {
        this.groupRecipient?.removeListener(this)
        this.groupRecipient = recipient
        this.groupRecipient?.addListener(this)

        setGroupRecipientAvatar()
    }

    fun setBitmap(bitmap: Bitmap) {
        group_splice_avatar.visibility = View.GONE
        member_single_avatar.visibility = View.VISIBLE
        background = null

        member_single_avatar.setPhoto(bitmap)
        member_single_avatar.radius = 10f
    }

    fun clearBitmap() {
        group_splice_avatar.visibility = View.GONE
        member_single_avatar.visibility = View.VISIBLE

        member_single_avatar.post {
            member_single_avatar.background = createBackground(width / 4f)
            member_single_avatar.radius = width / 4f
        }
    }

    
    private fun setCacheAvatar(path: String) {
        group_splice_avatar.visibility = View.VISIBLE
        member_single_avatar.visibility = View.GONE

        group_splice_avatar.post {
            var corner = layoutParams.width / 4
            if (corner == 0) corner = 10.dp2Px()
            background = createBackground(corner.toFloat())
            glide.asBitmap()
                    .load(Uri.fromFile(File(path)))
                    .error(R.drawable.common_group_default_logo)
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(corner)))
                    .listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                            return false
                        }

                        override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            loadCallback?.invoke(resource)
                            return false
                        }
                    })
                    .into(group_splice_avatar)
        }
    }

    private fun setImageResource(resId: Int) {
        group_splice_avatar.visibility = View.GONE
        member_single_avatar.visibility = View.VISIBLE
        background = null

        val drawable = getDrawable(resId)
        member_single_avatar.setPhoto(drawable)
        member_single_avatar.post {
            member_single_avatar.radius = width / 4f
        }

        loadCallback?.invoke((drawable as? BitmapDrawable)?.bitmap)
    }

    private fun setRecipientAvatar() {
        group_splice_avatar.visibility = View.GONE
        member_single_avatar.visibility = View.VISIBLE
        background = null

        member_single_avatar.setPhoto(privateRecipient)
        member_single_avatar.setOval(true)
    }

    private fun setGroupRecipientAvatar() {
        group_splice_avatar.visibility = View.GONE
        member_single_avatar.visibility = View.VISIBLE
        background = null

        member_single_avatar.setPhoto(groupRecipient)
        member_single_avatar.post {
            member_single_avatar.radius = width / 4f
        }
    }

    private fun createBackground(cornerRadius: Float): Drawable {
        val drawable = GradientDrawable()
        drawable.cornerRadius = cornerRadius
        drawable.setColor(Color.parseColor("#4DA8A8A8"))
        return drawable
    }

    fun setLoadCallback(callback: (Bitmap?) -> Unit) {
        loadCallback = callback
    }

    override fun onModified(recipient: Recipient) {
        if (this.privateRecipient == recipient) {
            setRecipientAvatar()
        }else if(this.groupRecipient == recipient) {
            setGroupRecipientAvatar()
        }
    }

    /**
     * 
     */
    fun clear() {
        privateRecipient?.removeListener(this)
        groupRecipient?.removeListener(this)
    }
}