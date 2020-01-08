package com.bcm.messenger.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.bcm.messenger.R
import com.bcm.messenger.chats.components.ChatForwardDialog
import com.bcm.messenger.chats.forward.ForwardRecentFragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.api.IContactsCallback
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.IForwardSelectProvider
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Send messages/files shared from system
 *
 * Created by Kin on 2018/10/8
 */
@Route(routePath = ARouterConstants.Activity.SYSTEM_SHARE)
class SystemShareActivity : SwipeBaseActivity(), IContactsCallback {
    private val TAG = "SystemShareActivity"

    private lateinit var glideRequest: GlideRequests
    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        disableClipboardCheck()
        glideRequest = GlideApp.with(this)

        setContentView(R.layout.activity_system_share)

        initView()


        handleIntent()

        setSwipeBackEnable(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
    }

    private fun initView() {
        val fragment = ForwardRecentFragment()
        fragment.setCallback(object : IForwardSelectProvider.ForwardSelectCallback {
            override fun onClickContact(recipient: Recipient) {
                onSelect(recipient)
            }
        })
        fragment.setContactSelectContainer(R.id.share_fragment_container)
        fragment.setGroupSelectContainer(R.id.share_fragment_container)
        initFragment(R.id.share_fragment_container, fragment, null)
    }

    private fun handleIntent() {
        if (!AMELogin.isLogin) {
            ALog.e(TAG, "No login data detected.")
            BcmRouter.getInstance().get(ARouterConstants.Activity.APP_LAUNCH_PATH).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).navigation(this)
            return
        }
        if (intent.action == null || intent.type == null) {
            ALog.e(TAG, "Intent action or type is null.")

            AmePopup.center.newBuilder()
                    .withTitle(getString(R.string.chats_forward_notice))
                    .withContent(getString(R.string.chats_share_data_problem))
                    .withOkTitle(getString(R.string.common_popup_ok))
                    .withOkListener {
                        finish()
                    }.show(this)
            return
        }
    }

    override fun onSelect(recipient: Recipient) {
        val dialog = ChatForwardDialog()
                .setRecipients(listOf(recipient))
                .setIsGroup(recipient.isGroupRecipient)
                .setIsShare(true)
                .setCallback { _, commentText ->
                    sendMessage(recipient, commentText)
                }
        val type = intent.type
        when {
            type?.startsWith("text/") == true -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) {
                    dialog.setForwardTextDialog(text)
                } else {
                    val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri ?: return
                    val lastSegment = uri.lastPathSegment
                    if (lastSegment != null) {
                        dialog.setForwardFileDialog(lastSegment)
                    }
                }
            }
            type?.startsWith("image/") == true -> {
                if (intent.action == Intent.ACTION_SEND) {
                    val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri ?: return
                    dialog.setForwardImageDialog(uri, 0L, glideRequest)
                } else {
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uris?.isNotEmpty() == true) {
                        dialog.setForwardImageDialog(uris[0], 0L, glideRequest)
                    }
                }
            }
            type?.startsWith("video/") == true -> {
                if (intent.action == Intent.ACTION_SEND) {
                    val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri ?: return
                    dialog.setForwardVideoDialog(uri, 0L, 0L, glideRequest)
                } else {
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uris?.isNotEmpty() == true) {
                        dialog.setForwardVideoDialog(uris[0], 0L, 0L, glideRequest)
                    }
                }
            }
            else -> {
                if (intent.action == Intent.ACTION_SEND) {
                    val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri ?: return
                    val lastSegment = uri.lastPathSegment
                    if (lastSegment != null) {
                        dialog.setForwardFileDialog(lastSegment)
                    }
                } else {
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uris?.isNotEmpty() == true) {
                        dialog.setForwardTextDialog(String.format(getString(R.string.chats_share_files_number), uris.size))
                    }
                }
            }
        }
        dialog.show(supportFragmentManager, "Share")
    }

    override fun onDeselect(recipient: Recipient) {}

    private fun sendMessage(recipient: Recipient, commentText: String) {
        val popup = AmePopup.progress.show(this, getString(R.string.chats_share_sending))
        var progress = 0
        disposable = Flowable.interval(0, 300, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    progress += 5
                    popup?.updateProgress(progress)
                    if (progress == 100) {
                        popup?.dismiss()
                        disposable?.dispose()
                        AmePopup.result.succeed(this, getString(R.string.chats_group_channel_share_sent), true) {
                            finish()
                        }
                    }
                }

        when {
            recipient.isBlocked -> {
                popup?.dismiss()
                disposable?.dispose()
                AmePopup.result.failure(this, getString(R.string.chats_forward_result_fail), true) {
                    finish()
                }
            }
            recipient.isGroupRecipient -> {
                SystemShareUtil.shareToGroupChat(getMasterSecret(), intent, recipient) {
                    popup?.dismiss()
                    disposable?.dispose()
                    AmePopup.result.succeed(this, getString(R.string.chats_group_channel_share_sent), true) {
                        finish()
                    }
                }
                if (commentText.isNotBlank()) {
                    SystemShareUtil.shareCommentToGroupChat(commentText, recipient)
                }
            }
            else -> {
                SystemShareUtil.shareToPrivateChat(intent, recipient, getMasterSecret()) {
                    popup?.dismiss()
                    disposable?.dispose()
                    AmePopup.result.succeed(this, getString(R.string.chats_group_channel_share_sent), true) {
                        finish()
                    }
                }
                if (commentText.isNotBlank()) {
                    SystemShareUtil.shareCommentToPrivateChat(commentText, recipient, getMasterSecret())
                }
            }
        }
    }
}