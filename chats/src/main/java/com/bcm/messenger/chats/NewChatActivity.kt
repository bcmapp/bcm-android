package com.bcm.messenger.chats

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.api.IContactsAction
import com.bcm.messenger.common.api.IContactsCallback
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.ConversationUtils
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_activity_new_chat.*

/**
 * Create a new chat
 */
class NewChatActivity : SwipeBaseActivity() {

    private lateinit var contactSelection: IContactsAction

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom_fast)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom_fast)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_new_chat)
        initView()
    }

    private fun initView() {
        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }
        })

        val newChatGroupView = LayoutInflater.from(this).inflate(R.layout.chats_new_chat_group, null)
        newChatGroupView.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.CHAT_GROUP_CREATE).navigation(this)
        }

        val f = BcmRouter.getInstance().get(ARouterConstants.Fragment.SELECT_SINGLE)
                .putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, false)
                .putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_INCLUDE_ME, true)
                .navigationWithCast<Fragment>()

        contactSelection = f as IContactsAction
        contactSelection.addSearchBar(this)
        contactSelection.addHeader(newChatGroupView)
        contactSelection.addEmptyShade(this)
        contactSelection.setContactSelectCallback(selectCallback)

        val tran = supportFragmentManager.beginTransaction()
        tran.add(R.id.container, f)
        tran.commit()
    }

    private val selectCallback = object : IContactsCallback {
        override fun onSelect(recipient: Recipient) {

            AmePopup.loading.show(this@NewChatActivity)
            ConversationUtils.getExistThreadId(recipient) {
                AmePopup.loading.dismiss()

                finish()

                BcmRouter.getInstance().get(ARouterConstants.Activity.CHAT_CONVERSATION_PATH)
                        .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
                        .putLong(ARouterConstants.PARAM.PARAM_THREAD, it)
                        .navigation()

            }
        }

        override fun onDeselect(recipient: Recipient) {
            //ignore
        }
    }
}
