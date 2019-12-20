package com.bcm.messenger.contacts

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.RecipientProfileLogic
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonShareView
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.contacts_fragment_contacts.*

/**
 * Created by wjh on 2018/2/26.
 */
@Route(routePath = ARouterConstants.Fragment.CONTACTS_HOST)
class ContactsFragment : BaseFragment(), RecipientModifiedListener {

    private val TAG = "ContactsFragment"
    private var mSelf: Recipient? = null

    private var mWaitForShortLink: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.contacts_fragment_contacts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            mSelf = Recipient.fromSelf(view.context, true)
            mSelf?.addListener(this)
        }catch (ex: Exception) {
            activity?.finish()
            return
        }
        contacts_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickRight() {
                context?.let {
                    val rightView = contacts_title_bar.getRightView().second ?: return
                    showAnim(rightView)
                    BcmPopupMenu.Builder(it)
                            .setMenuItem(listOf(
                                    BcmPopupMenu.MenuItem(getString(R.string.contacts_main_scan_and_add), R.drawable.contacts_menu_scan_icon),
                                    BcmPopupMenu.MenuItem(getString(R.string.contacts_main_invite_friend), R.drawable.contacts_menu_invite_icon)
                            ))
                            .setAnchorView(rightView)
                            .setSelectedCallback { index ->
                                when (index) {
                                    0 -> BcmRouter.getInstance().get(ARouterConstants.Activity.SCAN_NEW)
                                            .putBoolean(ARouterConstants.PARAM.SCAN.HANDLE_DELEGATE, true)
                                            .putInt(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_CONTACT)
                                            .navigation(activity)
                                    1 ->
                                    {
                                        mSelf?.let {
                                            doInvite(it)
                                        }
                                    }

                                }
                            }
                            .setDismissCallback {
                                hideAnim(rightView)
                            }
                            .show()
                }
            }
        })
    }

    override fun onModified(recipient: Recipient) {
        if (activity?.isFinishing == true) {
            return
        }
        if (mSelf == recipient) {
            if (mWaitForShortLink) {
                doInvite(recipient)
            }
        }
    }

    private fun doInvite(recipient: Recipient) {
        val shareLink = recipient.privacyProfile.shortLink
        if (shareLink.isNullOrEmpty()) {
            mWaitForShortLink = true
            AmeAppLifecycle.showLoading()
            RecipientProfileLogic.updateShareLink(AppContextHolder.APP_CONTEXT, recipient) {
                AmeAppLifecycle.hideLoading()
            }

        }else {
            mWaitForShortLink = false
            CommonShareView.Builder()
                    .setText(getString(R.string.common_invite_user_message, shareLink))
                    .setType(CommonShareView.Config.TYPE_TEXT)
                    .show(activity)
        }

    }

    private fun showAnim(view: View) {
        ObjectAnimator.ofFloat(view, "rotation", 0f, 45f).apply {
            duration = 250
        }.start()
    }

    private fun hideAnim(view: View) {
        ObjectAnimator.ofFloat(view, "rotation", 45f, 0f).apply {
            duration = 250
        }.start()
    }


}
