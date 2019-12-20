package com.bcm.messenger.chats.user

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.bean.SendContactEvent
import com.bcm.messenger.chats.components.ChatSendContactDialog
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.api.IContactsAction
import com.bcm.messenger.common.api.IContactsCallback
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.setDrawableLeft
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_activity_send_contact.*

/**
 * Created by Kin on 2018/8/15
 */
@Route(routePath = ARouterConstants.Activity.CONTACT_SEND)
class SendContactActivity : SwipeBaseActivity(), IContactsCallback {

    private val TAG = "SendContactActivity"
    private lateinit var chatRecipient: Recipient
    private var selectSet: MutableSet<Recipient> = mutableSetOf()
    private var mMultiMode: Boolean = false
    private var mContactsAction: IContactsAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_send_contact)

        val address = intent.getParcelableExtra<Address>(ARouterConstants.PARAM.PARAM_ADDRESS)
        chatRecipient = Recipient.from(this, address, true)

        initView()
    }

    private fun initView() {
        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                if (!mMultiMode) {
                    finish()
                }else {
                    mContactsAction?.setMultiMode(false)
                }
            }

            override fun onClickRight() {
                if (mMultiMode) {
                    prepareSend()
                }
                else {
                    mContactsAction?.setMultiMode(true)
                }
            }
        })

        val fragment = BcmRouter.getInstance().get(ARouterConstants.Fragment.SELECT_SINGLE)
                .putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, false)
                .putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_CHANGE_MODE, true)
                .putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_INCLUDE_ME, true)
                .navigationWithCast<Fragment>()

        if (fragment is IContactsAction) {
            mContactsAction = fragment
            fragment.addSearchBar(this)
            fragment.addEmptyShade(this)
            fragment.setContactSelectCallback(this)
        }

        supportFragmentManager.beginTransaction()
                .add(R.id.container, fragment)
                .commit()

        onModeChanged(false)
    }

    override fun onBackPressed() {
        if (mMultiMode) {
            mContactsAction?.setMultiMode(false)
        }else {
            super.onBackPressed()
        }
    }

    override fun onSelect(recipient: Recipient) {
        if (!mMultiMode) {
            selectSet.clear()
            selectSet.add(recipient)
            prepareSend()
        }else {
            selectSet.add(recipient)
            val right = title_bar?.getRightView()?.second as? TextView
            right?.text = "${getString(R.string.chats_done)}(${selectSet.size})"
        }
    }

    override fun onDeselect(recipient: Recipient) {
        selectSet.remove(recipient)
        if (mMultiMode) {
            val right = title_bar?.getRightView()?.second as? TextView
            right?.text = "${getString(R.string.chats_done)}(${selectSet.size})"
        }
    }

    override fun onModeChanged(multiSelect: Boolean) {
        ALog.i(TAG, "onModeChanged: $multiSelect")
        mMultiMode = multiSelect
        val left = title_bar?.getLeftView()?.second as? TextView
        val right = title_bar?.getRightView()?.second as? TextView
        selectSet.clear()
        if (!multiSelect) {
            left?.text = ""
            left?.setDrawableLeft(R.drawable.common_back_arrow_black_icon)
            right?.text = getString(R.string.common_multi_select)
        }else {
            left?.text = getString(R.string.chats_select_mode_cancel_btn)
            left?.setDrawableLeft(0)
            right?.text = "${getString(R.string.chats_done)}(${selectSet.size})"
        }
    }

    private fun prepareSend() {
        if (selectSet.isEmpty()) {
            return
        }
        ChatSendContactDialog()
                .setChatRecipient(chatRecipient)
                .setSendRecipient(selectSet.toList())
                .setSendCallback { sendList, comment ->
                    val dataList = sendList.map {
                        AmeGroupMessage.ContactContent(it.bcmName ?: it.address.format(), it.address.serialize(), "")
                    }
                    if (sendList.isNotEmpty()) {
                        var groupId = 0L
                        if (chatRecipient.isGroupRecipient) {
                            groupId = chatRecipient.groupId
                        }
                        val event = SendContactEvent(groupId, dataList, comment.toString())
                        try {
                            setResult(Activity.RESULT_OK, Intent().apply {
                                putExtra(ARouterConstants.PARAM.PARAM_DATA, event.toString())
                            })
                        }catch (ex: Exception) {
                            ALog.e(TAG, "prepareSend error", ex)
                            setResult(Activity.RESULT_CANCELED)
                        }

                    }else {
                        setResult(Activity.RESULT_CANCELED)
                    }
                    finish()
                }
                .show(supportFragmentManager, "Dialog")
    }
}