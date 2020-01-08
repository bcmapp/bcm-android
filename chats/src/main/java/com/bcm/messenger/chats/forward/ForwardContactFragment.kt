package com.bcm.messenger.chats.forward

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.api.IContactsAction
import com.bcm.messenger.common.api.IContactsCallback
import com.bcm.messenger.common.provider.IForwardSelectProvider.ForwardSelectCallback
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_fragment_forward_contact.*

class ForwardContactFragment : BaseFragment(), IContactsCallback {
    private var callback: ForwardSelectCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_forward_contact, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        forward_contact_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                fragmentManager?.popBackStack()
            }
        })

        activity?.window?.setStatusBarLightMode()
    }

    override fun onStart() {
        super.onStart()
        val fragment = BcmRouter.getInstance().get(ARouterConstants.Fragment.SELECT_SINGLE)
                .putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, false)
                .putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_CONTACT_GROUP,
                        arguments?.getBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_CONTACT_GROUP, false) ?: false)
                .putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_INCLUDE_ME, true)
                .putSerializable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
                .navigationWithCast<Fragment>()

        if (fragment is IContactsAction) {
            fragment.addSearchBar(context ?: AppContextHolder.APP_CONTEXT)
            fragment.addEmptyShade(context ?: AppContextHolder.APP_CONTEXT)
            fragment.setContactSelectCallback(this)
        }

        AmeDispatcher.mainThread.dispatch({
            fragmentManager?.beginTransaction()
                    ?.add(R.id.forward_contact_content, fragment)
                    ?.commit()
        }, 300)
    }

    fun setCallback(callback: ForwardSelectCallback?) {
        this.callback = callback
    }

    override fun onSelect(recipient: Recipient) {
        callback?.onClickContact(recipient)
    }

    override fun onDeselect(recipient: Recipient) {

    }
}