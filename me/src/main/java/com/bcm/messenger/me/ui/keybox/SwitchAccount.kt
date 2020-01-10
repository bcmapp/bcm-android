package com.bcm.messenger.me.ui.keybox

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * bcm.social.01 2018/11/6.
 */
object SwitchAccount {

    class SwitchPopupHeader(private val switchToRecipient: Recipient?, private val switchForLogOut: Boolean) : AmeBottomPopup.CustomViewCreator, RecipientModifiedListener {

        var clear: Boolean = false
        private var photoView: IndividualAvatarView? = null
        private var nickNameView: TextView? = null
        private var titleView: TextView? = null

        override fun onCreateView(parent: ViewGroup): View? {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.me_layout_logout_selection_view, parent, true)

            photoView = view.findViewById(R.id.user_icon)
            nickNameView = view.findViewById(R.id.user_nick_name)
            titleView = view.findViewById(R.id.logout_option_title)

            val checkBox = view.findViewById<CheckBox>(R.id.logout_option_check)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                clear = isChecked
            }

            switchToRecipient?.addListener(this)
            initView(view.context, switchToRecipient)

            return view
        }

        private fun initView(context: Context, recipient: Recipient?) {
            if (!switchForLogOut) {
                photoView?.visibility = View.VISIBLE
                nickNameView?.visibility = View.VISIBLE
                photoView?.setPhoto(recipient)

                var name = recipient?.name ?: ""
                val account = AmeLoginLogic.getAccount(recipient?.address?.serialize() ?: "")
                if (account?.name?.isNotEmpty() == true) {
                    name = account.name
                }

                nickNameView?.text = name

                titleView?.text = context.getString(R.string.me_chats_are_you_sure_to_switch)
            } else {
                photoView?.visibility = View.GONE
                nickNameView?.visibility = View.GONE
                titleView?.text = context.getString(R.string.me_logout_tip_title)
            }
        }

        override fun onDetachView() {
            switchToRecipient?.removeListener(this)
        }

        override fun onModified(recipient: Recipient) {
            AmeDispatcher.mainThread.dispatch {
                if (switchToRecipient == recipient) {
                    initView(AppContextHolder.APP_CONTEXT, recipient)
                }
            }
        }
    }

    fun switchAccount(accountContext: AccountContext, context: Context, switchTo: Recipient?) {
        if (!AppUtil.checkNetwork()) {
            ALog.e("SwitchAccountAdapters", "network disconnected")
            return
        }

        val activity = context as? Activity
        if (null == activity || activity.isFinishing) {
            return
        }

        val switchForLogOut = true
        val viewCreator = SwitchPopupHeader(switchTo, switchForLogOut)

        var option = activity.getString(R.string.me_logout_tip_out)
        var color = AmeBottomPopup.PopupItem.CLR_RED
        if (!switchForLogOut) {
            option = context.getString(R.string.me_switch_action_text)
            color = AmeBottomPopup.PopupItem.CLR_BLACK
        }

        AmePopup.bottom.newBuilder()
                .withCustomView(viewCreator)
                .withPopItem(AmeBottomPopup.PopupItem(option, color) {
                    AmePopup.loading.show(activity as? FragmentActivity)
                    Observable.create(ObservableOnSubscribe<Boolean> {
                        try {
                            AmeLoginLogic.quit(accountContext, viewCreator.clear)
                            it.onNext(true)
                        } catch (ex: Exception) {
                            it.onNext(false)
                        } finally {
                            it.onComplete()
                        }

                    }).subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                AmePopup.loading.dismiss()

                                if (AMELogin.isLogin) {
                                    BcmRouter.getInstance().get(ARouterConstants.Activity.APP_HOME_PATH)
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .startBcmActivity(AMELogin.majorContext)
                                } else {
                                    BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .navigation()
                                }
                                activity.finish()
                            }

                }).withCancelable(true)
                .withDoneTitle(context.getString(R.string.common_cancel))
                .show(activity as? FragmentActivity)
    }
}