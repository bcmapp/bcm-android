package com.bcm.messenger.me.ui.login.backup

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.pinlock.PinInputActivity
import com.bcm.messenger.me.ui.qrcode.ShowQRCodeActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_fragment_check_password.*
import java.lang.ref.WeakReference


class CheckBackupPasswordFragment : AbsRegistrationFragment() {
    private var action: Int = 0
    private var accountId: String? = null
    private var recipient: Recipient? = null
    private var needFinish: Boolean = false

    companion object {
        const val BACKUP_JUMP_ACTION = "BACKUP_JUMP_ACTION"
        const val NEED_FINISH = "NEED_FINISH"
        const val SHOW_QRCODE_BACKUP = 1
        const val DELETE_PROFILE = 2
        const val DELETE_BACKUP = 3
        const val CHANGE_PIN = 4
        const val CLEAR_PIN = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.apply {
            action = getInt(BACKUP_JUMP_ACTION, SHOW_QRCODE_BACKUP)
            accountId = getString(VerifyKeyActivity.ACCOUNT_ID)
            needFinish = getBoolean(NEED_FINISH, false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_check_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        recipient = null
    }

    private fun initViews() {

        relogin_input_pin_header.text = when (action) {
            DELETE_PROFILE -> resources.getString(R.string.me_keybox_delete_btn)
            SHOW_QRCODE_BACKUP -> resources.getString(R.string.me_show_qr_code)
            CHANGE_PIN -> resources.getString(R.string.me_change_pin)
            CLEAR_PIN -> resources.getString(R.string.me_clear_pin)
            else -> resources.getString(R.string.me_show_qr_code)
        }

        backup_input_pin_back.setOnClickListener {
            if (accountId != null) {
                activity?.finish()
            } else {
                activity?.apply { supportFragmentManager.popBackStack() }
            }
        }

        backup_input_pin_editText.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD or EditorInfo.TYPE_CLASS_TEXT

        backup_pin_input_done_button.setOnClickListener {
            if (!TextUtils.isEmpty(backup_input_pin_editText.text)) {
                AmePopup.loading.show(activity, false)
                checkPasswordIsRight(backup_input_pin_editText.text.toString()) {
                    AmePopup.loading.dismiss()
                    if (it){
                        activity?.apply {
                            if (action == SHOW_QRCODE_BACKUP) {
                                activity?.apply { supportFragmentManager.popBackStack() }
                                
                                val intent = Intent(this, ShowQRCodeActivity::class.java)
                                if (!arguments?.getString(VerifyKeyActivity.ACCOUNT_ID).isNullOrEmpty()) {
                                    intent.putExtra(VerifyKeyActivity.ACCOUNT_ID, arguments?.getString(VerifyKeyActivity.ACCOUNT_ID))
                                }
                                startActivity(intent)
                                if (needFinish)
                                    activity?.finish()
                            } else if (action == DELETE_BACKUP) {
                                text_result_view.setResult(getString(R.string.me_str_delete_account_key_success))
                                check_password_view.visibility = View.GONE
                                text_result_view.visibility = View.VISIBLE

                                text_result_view.postDelayed({
                                    
                                    AmeLoginLogic.accountHistory.resetBackupState(AMESelfData.uid)
                                    BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            .navigation(activity)
                                },2000)

                            } else if (action == DELETE_PROFILE) {
                                text_result_view.setResult(getString(R.string.me_str_delete_account_key_success))
                                check_password_view.visibility = View.GONE
                                text_result_view.visibility = View.VISIBLE
                                val uid = accountId
                                if (null != uid){
                                    AmeLoginLogic.accountHistory.deleteAccount(uid)
                                }

                                hideKeyboard()
                                text_result_view.postDelayed({
                                    
                                    if (needFinish){
                                        if (AmeLoginLogic.isLogin()){
                                            activity?.finish()
                                        }
                                        else{
                                            BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                    .navigation(activity)
                                        }
                                    }
                                }, 2000)
                            } else if (action == CLEAR_PIN) {
                                AmePopup.result.succeed(activity, resources.getString(R.string.me_pin_clear))
                                AmePinLogic.disablePin()
                                backup_pin_input_done_button.postDelayed({
                                    finish()
                                }, 1000)
                            } else if (action == CHANGE_PIN) {
                                BcmRouter.getInstance().get(ARouterConstants.Activity.PIN_INPUT)
                                        .putInt(PinInputActivity.INPUT_STYPE, PinInputActivity.INPUT_PIN)
                                        .navigation()
                                finish()
                            }
                        }
                    } else {
                        
                        Toast.makeText(activity, getString(R.string.login_password_error), Toast.LENGTH_SHORT).show()
                    }
                }

            } else {
                
                Toast.makeText(activity, R.string.me_input_pin_hint, Toast.LENGTH_SHORT).show()
            }

        }

        fetchProfile(accountId)
    }

    private fun checkPasswordIsRight(password: String, result:(right:Boolean)->Unit) {
        AmeDispatcher.io.dispatch {
            var right = false
            try {
                val id = this.accountId
                if (id != null) {
                    val account = AmeLoginLogic.getAccount(id)
                    if (null != account){
                        right = AmeLoginLogic.accountHistory.getPrivateKeyWithPassword(account, password) != null
                    }
                }

            } catch (e: Exception) {
                ALog.e("CheckPassword", e)
            }

            AmeDispatcher.mainThread.dispatch {
                result(right)
            }
        }
    }

    private fun fetchProfile(accountId: String?) {
        if (!accountId.isNullOrEmpty()) {
            val account = AmeLoginLogic.accountHistory.getAccount(accountId)
            val realUid: String? = account?.uid
            val name: String? = account?.name
            val avatar: String? = account?.avatar

            if (!realUid.isNullOrEmpty()) {

                val weakThis = WeakReference(this)
                Observable.create(ObservableOnSubscribe<Recipient> { emitter ->
                    try {
                        val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(realUid), false)
                        val finalAvatar = if (BcmFileUtils.isExist(avatar)) {
                            avatar
                        }else {
                            null
                        }
                        recipient.setProfile(recipient.profileKey, name, finalAvatar)
                        emitter.onNext(recipient)
                    } finally {
                        emitter.onComplete()
                    }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ recipient ->

                            weakThis.get()?.backup_input_pin_nikename?.text = recipient.name
                            weakThis.get()?.relogin_input_pin_avatar?.setPhoto(recipient, IndividualAvatarView.KEYBOX_PHOTO_TYPE)

                        }, { _ ->

                        })

            }
        }else {
            activity?.finish()
        }
    }
}