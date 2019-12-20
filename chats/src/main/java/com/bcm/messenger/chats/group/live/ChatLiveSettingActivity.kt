package com.bcm.messenger.chats.group.live

import android.os.Bundle
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.chats_activity_live_settings.*

class ChatLiveSettingActivity : SwipeBaseActivity(), LinkSetImpl {

    private val TAG = "ChatLiveSettingActivity"
    private var previewFragment: ChatLivePreviewFragment? = null
    private var gid = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom_fast_no_alpha)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom_fast_no_alpha)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_live_settings)

        chats_live_settings_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                if (gid != -1L) {
                    previewFragment?.publish(gid)
                } else {
                    ALog.e(TAG, "gid error")
                }
            }
        })
        val fragment = ChatLiveLinkSetFragment()
        fragment.setLinkSetImpl(this)
        supportFragmentManager.beginTransaction()
                .replace(R.id.chats_live_settings_content, fragment)
                .commitAllowingStateLoss()

        gid = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1L)
    }

    override fun next(url: String, playUrl: String, duration: Long) {
        if (duration == 0L) {
            return
        }

        val previewFragment = ChatLivePreviewFragment()
        val arg = Bundle()
        arg.putString(ChatLivePreviewFragment.LIVE_URL, url)
        arg.putString(ChatLivePreviewFragment.LIVE_PLAY_URL, playUrl)
        arg.putLong(ChatLivePreviewFragment.LIVE_DURATION, duration)
        previewFragment.arguments = arg
        supportFragmentManager.beginTransaction()
                .replace(R.id.chats_live_settings_content, previewFragment)
                .commitAllowingStateLoss()
        chats_live_settings_bar.setRightText(getString(R.string.chats_live_settings_publish))
        chats_live_settings_bar.setCenterText(getString(R.string.chats_live_settings_preview_title))

        this.previewFragment = previewFragment
    }

    override fun finish() {
        hideKeyboard()
        super.finish()
    }
}