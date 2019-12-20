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
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * bcm.social.01 2018/11/6.
 */
class SwitchAccountAdapter {

    class SwitchPopupHeader(private val switchToRecipient: Recipient?, private val switchForLogOut: Boolean) : AmeBottomPopup.CustomViewCreator, RecipientModifiedListener {

        var clear: Boolean = false
        private var photoView: IndividualAvatarView? = null
        private var nickNameView: TextView? = null
        private var titleView: TextView? = null

        override fun onCreateView(parent: ViewGroup): View? {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.me_layout_logout_selection_view, parent, true)

            photoView = view.findViewById<IndividualAvatarView>(R.id.user_icon)
            nickNameView = view.findViewById<TextView>(R.id.user_nick_name)
            titleView = view.findViewById<TextView>(R.id.logout_option_title)

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

    fun switchAccount(context: Context, uid: String, switchTo: Recipient?) {
        if (uid.isEmpty()) {
            ALog.e("SwitchAccountAdapters", "invalid account")
            return
        }

        if (!AppUtil.checkNetwork()) {
            ALog.e("SwitchAccountAdapters", "network disconnected")
            return
        }

        val activity = context as? Activity
        if (null == activity || activity.isFinishing) {
            return
        }

        val switchForLogOut = (uid == AMESelfData.uid)
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
                    if (!switchForLogOut) {
                        AmeLoginLogic.accountHistory.saveLastLoginUid(uid)
                    }

                    AmePopup.loading.show(activity as? FragmentActivity)
                    Observable.create(ObservableOnSubscribe<Boolean> {
                        try {
                            AmeLoginLogic.quit(viewCreator.clear)
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

                                AmeModuleCenter.onLoginStateChanged("")

                                activity.startActivity(Intent(activity, VerifyKeyActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                                    putExtra(VerifyKeyActivity.BACKUP_JUMP_ACTION, VerifyKeyActivity.LOGIN_PROFILE)
                                    putExtra(RegistrationActivity.RE_LOGIN_ID, uid)
                                })

                                activity.finish()
                            }

                }).withCancelable(true)
                .withDoneTitle(context.getString(R.string.common_cancel))
                .show(activity as? FragmentActivity)
    }
}