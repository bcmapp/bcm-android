package com.bcm.messenger.adhoc.ui.channel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocChannel
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.ui.AdHocSessionSelectionActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AppUtil
import kotlinx.android.synthetic.main.adhoc_activity_invite_join.*

/**
 *
 * Invite Join Offline chat
 * Created by wjh on 2019-08-19
 */
class AdHocInviteJoinActivity : AccountSwipeBaseActivity() {
    private val SELECT_SESSION_REQ = 1000
    private var sessionId = ""
    private var channel: AdHocChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.adhoc_activity_invite_join)

        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }

            override fun onClickRight() {

                AmePopup.bottom.newBuilder()
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.adhoc_invite_copy_item)) {
                            val text = "AirChat Name: ${invite_room_et.text}\n" +
                                    "Password: ${invite_password_et.text}"
                            AppUtil.saveCodeToBoard(this@AdHocInviteJoinActivity, text)

                            AmePopup.result.succeed(this@AdHocInviteJoinActivity, getString(R.string.common_copied), true)
                        })
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.adhoc_invite_forward_item)) {
                            val intent = Intent(this@AdHocInviteJoinActivity, AdHocSessionSelectionActivity::class.java).apply {
                                putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, sessionId)
                            }
                            startActivityForResult(intent, SELECT_SESSION_REQ)
                        })
                        .withDoneTitle(getString(R.string.common_cancel))
                        .withCancelable(false)
                        .show(this@AdHocInviteJoinActivity)
            }
        })


        val cid = intent.getStringExtra(ARouterConstants.PARAM.ADHOC.CID) ?: ""
        sessionId = intent.getStringExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION)?:""
        channel = AdHocChannelLogic.get(accountContext).getChannel(cid)

        invite_room_et.text = channel?.viewName()
        invite_password_et.text = channel?.passwd

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_SESSION_REQ && resultCode == Activity.RESULT_OK) {
            val newSessionId = data?.getStringExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION)
            val newChannel = this.channel
            if (newChannel != null && !newSessionId.isNullOrEmpty()) {
                val myName = try {
                    Recipient.major().name
                }catch (ex: Exception) {
                    null
                }
                if (myName != null) {
                    AdHocMessageLogic.get(accountContext).sendInvite(newSessionId, myName, newChannel.channelName, newChannel.passwd)
                    AmePopup.result.succeed(this, getString(R.string.adhoc_invite_forward_success), true)

                }
            }else {
                AmePopup.result.failure(this, getString(R.string.adhoc_invite_forward_fail), true)
            }
        }
    }

}