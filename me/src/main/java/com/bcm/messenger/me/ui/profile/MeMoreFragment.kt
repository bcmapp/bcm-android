package com.bcm.messenger.me.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.RecipientProfileLogic
import com.bcm.messenger.common.event.HomeTabEvent
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAdHocModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.PullDownLayout
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.common.utils.getStatusBarHeight
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmeNoteLogic
import com.bcm.messenger.me.ui.feedback.AmeFeedbackActivity
import com.bcm.messenger.me.ui.login.backup.AccountSecurityActivity
import com.bcm.messenger.me.ui.note.AmeNoteActivity
import com.bcm.messenger.me.ui.note.AmeNoteUnlockActivity
import com.bcm.messenger.me.ui.setting.AboutActivity
import com.bcm.messenger.me.ui.setting.SettingActivity
import com.bcm.messenger.me.utils.BcmUpdateUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QREncoder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_fragment_more.*
import kotlinx.android.synthetic.main.me_fragment_more_qr_code.*
import java.lang.ref.WeakReference

/**
 * Created by Kin on 2019/7/1
 */
class MeMoreFragment : Fragment(), RecipientModifiedListener {

    companion object {
        private const val TAG = "MeMoreFragment"
    }

    private lateinit var recipient: Recipient
    private var isTopViewShown = false
    private var qrCodeImage: Bitmap? = null
    private var mLastShortLink: String? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_more, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ALog.i(TAG, "onViewCreated")
        try {
            recipient = Recipient.fromSelf(AppContextHolder.APP_CONTEXT, true)
            recipient.addListener(this)
        }catch (ex: Exception) {
            ALog.e(TAG, "onViewCreated error, recipient from self fail", ex)
            activity?.finish()
            return
        }

        initTitleAndTopView()
        initView()
        setSelfData()
        checkBackupNotice(AmeLoginLogic.accountHistory.getBackupTime(AMESelfData.uid) > 0)
    }

    override fun onResume() {
        super.onResume()

        try {
            checkBackupNotice(AmeLoginLogic.accountHistory.getBackupTime(AMESelfData.uid) > 0)
            checkVersionUpdate()

            if (recipient.needRefreshProfile()) {
                RecipientProfileLogic.forceToFetchProfile(recipient, callback = object: RecipientProfileLogic.ProfileDownloadCallback {
                    override fun onDone(recipient: Recipient, isThrough: Boolean) {
                        RecipientProfileLogic.checkNeedDownloadAvatarWithAll(recipient)
                    }
                })
            }else {
                ALog.w(TAG, "on need refresh profile")
                RecipientProfileLogic.checkNeedDownloadAvatarWithAll(recipient)
            }
        } catch (e:Throwable) {
            ALog.e(TAG, "onResume", e)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }
    }

    private fun initTitleAndTopView() {
        val statusBarHeight = context?.getStatusBarHeight() ?: 0
        val screenWidth = AppContextHolder.APP_CONTEXT.getScreenWidth()
        val titleBarHeight = statusBarHeight + 60.dp2Px()
        val qrMaxTop = 94.dp2Px()
        val qrCodeViewHeight = screenWidth - 120.dp2Px() + qrMaxTop + 60.dp2Px()

        me_more_qr_code_icon.layoutParams = (me_more_qr_code_icon.layoutParams as ConstraintLayout.LayoutParams).apply {
            setMargins(0, statusBarHeight, 0, 0)
        }
        me_more_fill.layoutParams = (me_more_fill.layoutParams as ConstraintLayout.LayoutParams).apply {
            height = titleBarHeight
        }
        me_more_top_view.layoutParams = (me_more_top_view.layoutParams as LinearLayout.LayoutParams).apply {
            height = titleBarHeight
        }
        me_more_qr_code_icon.setOnClickListener {
            if (!isTopViewShown) {
                me_more_pull_layout.expandTopView()
            }
        }
        me_more_qr_code.layoutParams = me_more_qr_code.layoutParams.apply {
            height = qrCodeViewHeight
        }

        me_more_pull_layout.setClickToCloseWhenExpanded()
        me_more_pull_layout.setTopView(me_more_top_view, screenWidth + titleBarHeight, titleBarHeight)
        me_more_pull_layout.setScrollView(me_more_scroll_view)
        me_more_pull_layout.setCallback(object : PullDownLayout.PullDownLayoutCallback() {
            private val qrCodeWidth = screenWidth - 120.dp2Px()
            private val dp60 = 60.dp2Px()
            private val dp40 = 40.dp2Px()
            private val dp15 = 15.dp2Px()

            override fun onTopViewHeightChanged(height: Int, front: Int) {
                isTopViewShown = height > titleBarHeight
                val alpha = (height.toFloat() - titleBarHeight) / screenWidth

                val codeSize = ((qrCodeWidth.toFloat() - dp40) * alpha).toInt() + dp40

                val xPercentage = (dp60 * alpha).toInt()
                val codeMarginRight = if (xPercentage < dp15) dp15 else xPercentage
                var codeX = screenWidth - xPercentage - codeSize
                if (codeX < dp60) codeX = dp60
                val currentY = (statusBarHeight + qrMaxTop * alpha).toInt()

                me_more_qr_code.setPadding(codeX, currentY, codeMarginRight, qrCodeViewHeight - codeSize - currentY)

                me_more_qr_code_icon.layoutParams = (me_more_qr_code_icon.layoutParams as ConstraintLayout.LayoutParams).apply {
                    setMargins(0, (screenWidth / 2.5 * alpha).toInt() + statusBarHeight, (screenWidth / 3 * alpha).toInt(), 0)
                }

                me_more_title_bar.alpha = 1 - alpha
                me_more_qr_code_icon.alpha = 1 - alpha
                me_more_qr_code.alpha = alpha
                me_more_scan_title.alpha = alpha
                me_more_scan.alpha = alpha

                if (alpha == 0f) {
                    me_more_scan.visibility = View.GONE
                } else if (me_more_scan.visibility == View.GONE) {
                    me_more_scan.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun initView() {
        me_more_self.setOnClickListener {
            if (checkOpenAndMultiClick()) {
                val intent = Intent(activity, ProfileActivity::class.java)
                startActivity(intent)
            }
        }

        me_more_keybox.setOnClickListener {
            if (checkOpenAndMultiClick()) {
                SuperPreferences.setAccountBackupRedPoint(context, AMESelfData.uid, true)
                val intent = Intent(activity, AccountSecurityActivity::class.java)
                startActivity(intent)
            }
        }

        me_more_data_vault.setOnClickListener {
            if (checkOpenAndMultiClick()) {
                if (AmeNoteLogic.getInstance().isLocked()) {
                    val intent = Intent(activity, AmeNoteUnlockActivity::class.java)
                    startActivity(intent)

                } else {
                    val intent = Intent(activity, AmeNoteActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        me_more_wallet.setOnClickListener {
            if (checkOpenAndMultiClick()) {
                BcmRouter.getInstance().get(ARouterConstants.Activity.WALLET_MAIN).navigation(context)
            }
        }

        me_more_air_chat.setOnClickListener {
            if (checkOpenAndMultiClick()) {
                val adhocProvider = AmeProvider.get<IAdHocModule>(ARouterConstants.Provider.PROVIDER_AD_HOC)
                adhocProvider?.configHocMode()
            }
        }

        me_more_settings.setOnClickListener {
            if (checkOpenAndMultiClick()) {
                val intent = Intent(activity, SettingActivity::class.java)
                startActivity(intent)
            }
        }

        me_more_feedback.setOnClickListener {
            if (checkOpenAndMultiClick()) {
                val intent = Intent(activity, AmeFeedbackActivity::class.java)
                startActivity(intent)
            }
        }

        me_more_about.setOnClickListener {
            if (checkOpenAndMultiClick()) {
                val intent = Intent(activity, AboutActivity::class.java)
                startActivity(intent)
            }
        }

        me_more_scan.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance().get(ARouterConstants.Activity.SCAN_NEW)
                    .putBoolean(ARouterConstants.PARAM.SCAN.HANDLE_DELEGATE, true)
                    .putInt(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_CONTACT)
                    .navigation(activity)
        }
    }

    private fun setSelfData() {
        ALog.d(TAG, "Update self data")
        me_more_self_avatar.showPrivateAvatar(recipient)
        me_more_self_name.text = recipient.name
        me_more_self_id.text = "${getString(R.string.me_id_title)}: ${AMESelfData.uid}"
        initQRCode()
    }

    private fun checkBackupNotice(isHasBackup: Boolean) {
        if (isHasBackup) {
            me_more_keybox.hideTip()
        } else {
            me_more_keybox.setTip(getString(R.string.me_not_backed_up), R.drawable.common_not_backup_icon)
        }
        RxBus.post(HomeTabEvent(HomeTabEvent.TAB_ME, showDot = !isHasBackup))
    }

    private fun initQRCode() {
        if (recipient.isResolving) {
            return
        }
        val shortLink = recipient.privacyProfile.shortLink
        if (shortLink.isNullOrEmpty()) {
            RecipientProfileLogic.updateShareLink(AppContextHolder.APP_CONTEXT, recipient) {

            }

        }else {
            if (shortLink != mLastShortLink) {
                mLastShortLink = shortLink
                Observable.create<Bitmap> {
                    ALog.d(TAG, "initQrCode: $shortLink")
                    val qrEncoder = QREncoder(shortLink, dimension = 250.dp2Px(), charset = "utf-8")
                    val bitmap = qrEncoder.encodeAsBitmap()
                    it.onNext(bitmap)
                    it.onComplete()

                }.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            qrCodeImage = it
                            me_more_qr_code.setImageBitmap(qrCodeImage)
                        }, {
                            ALog.e(TAG, "initQrCode error", it)
                            qrCodeImage = null
                            me_more_qr_code.setImageBitmap(qrCodeImage)
                        })
            }
        }

    }

    private fun checkVersionUpdate() {
        val weakActivity = WeakReference(activity)
        val weakThis = WeakReference(this)

        BcmUpdateUtil.checkUpdate { hasUpdate, forceUpdate, _ ->
            val wThis = weakThis.get()
            val wActivity = weakActivity.get()
            if (null != wThis && null != wActivity && !wActivity.isFinishing)
                if (hasUpdate) {
                    wThis.me_more_about?.setTip(AppContextHolder.APP_CONTEXT.getString(R.string.me_new_version_description))
                    if (forceUpdate) {
                        weakActivity.get()?.let {
                            BcmUpdateUtil.showForceUpdateDialog()
                        }
                    }
                } else {
                    wThis.me_more_about?.hideTip()
                }
        }
    }

    private fun checkOpenAndMultiClick(): Boolean {
        if (isTopViewShown) {
            me_more_pull_layout.closeTopView()
            return false
        }

        if (QuickOpCheck.getDefault().isQuick){
            return false
        }

        return true
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        if (!isVisibleToUser) {
            closeTopView()
        }
        super.setUserVisibleHint(isVisibleToUser)
    }

    fun isTopViewExpanded() = isTopViewShown

    fun closeTopView() {
        me_more_pull_layout?.closeTopView()
    }

    override fun onModified(recipient: Recipient) {
        if (activity?.isFinishing != false) return
        if (this.recipient == recipient) {
            setSelfData()
        }
    }
}