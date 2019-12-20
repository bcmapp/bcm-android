package com.bcm.messenger.me.ui.destroy

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.utils.MeConfirmDialog
import com.bcm.messenger.utility.AppContextHolder
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_fragment_destroy_check_password.*
import java.lang.ref.WeakReference

/**
 * Created by Kin on 2018/9/19
 */
class DestroyCheckPasswordFragment : Fragment() {

    private val TAG = "DestroyCheckPasswordFragment"

    private var mUid: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_destroy_check_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        destroy_back_icon.setOnClickListener {
            fragmentManager?.popBackStack()
        }

        destroy_password_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                destroy_password_input.background = if (s != null && s.isNotEmpty()) {
                    context?.getDrawable(R.drawable.me_register_input_bg)
                } else {
                    context?.getDrawable(R.drawable.me_register_input_error_bg)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        destroy_confirm.setOnClickListener {
            gotoUnregister()
        }

        mUid = arguments?.getString(ARouterConstants.PARAM.PARAM_ACCOUNT_ID)
        fetchProfile(mUid)
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

                            weakThis.get()?.destroy_name?.text = recipient.name
                            weakThis.get()?.destroy_avatar?.setPhoto(recipient, IndividualAvatarView.KEYBOX_PHOTO_TYPE)

                        }, { _ ->

                        })

            }

        } else {
            activity?.finish()
        }
    }

    /**
     * destroy account
     */
    private fun gotoUnregister() {
        val uid = mUid
        if (uid.isNullOrEmpty()) {
            AmePopup.result.failure(activity, "account is null")
        }else {
            val psw = destroy_password_input.text.toString()
            AmePopup.result.notice(activity, getString(R.string.me_destroy_other_client_command_sent), true)
            AmeLoginLogic.unregister(uid, psw) { succeed, error ->
                AmePopup.result.dismiss()

                if (succeed) {
                    activity?.let {
                        MeConfirmDialog.showConfirm(it, it.getString(R.string.me_destroy_account_confirm_title),
                                it.getString(R.string.me_destroy_account_confirm_notice), it.getString(R.string.me_destroy_account_confirm_button)) { _ ->
                            (it as? DestroyAccountActivity)?.gotoLogin()
                        }

                    }

                } else {
                    AmePopup.result.failure(activity, error)
                }
            }
        }
    }

}