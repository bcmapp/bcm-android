package com.bcm.messenger.me.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_fragment_ban_reset_password.*
import org.whispersystems.libsignal.ecc.ECKeyPair

/**

 * Created by zjl on 2018/9/7.
 */
class BanResetPasswordFragment : AbsRegistrationFragment() {

    private val TAG = "BanResetPasswordFragment"

    private var keyPair: ECKeyPair? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_ban_reset_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ban_reset_create_account.setOnClickListener {
            AmePopup.tipLoading.show(activity)
            Observable.create(ObservableOnSubscribe<ECKeyPair> {
                try {
                    it.onNext(IdentityKeyUtil.generateIdentityKeys(context))
                } finally {
                    it.onComplete()
                }
            }).subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        keyPair = it
                        if (keyPair != null) {
                            AmeLoginLogic.queryChallengeTarget(it) { target ->

                                AmePopup.tipLoading.dismiss()
                                if (target != null && activity != null) {
                                    val f = GenerateKeyFragment2()
                                    val arg = Bundle()
                                    arg.putInt("action", 1)
                                    arg.putString("target", target)
                                    f.arguments = arg
                                    activity!!.supportFragmentManager.beginTransaction()
                                            .replace(R.id.register_container, f, "generate_key_fragment")
                                            .addToBackStack("ban_reset_password_fragment")
                                            .commitAllowingStateLoss()
                                    f.setKeyPair(it)
                                }
                            }
                        } else {
                            AmePopup.tipLoading.dismiss()
                        }
                    }, {
                        AmePopup.tipLoading.dismiss()
                        ALog.e(TAG, it.toString())
                    })
        }

        ban_back.setOnClickListener {
            activity?.apply { supportFragmentManager.popBackStack() }
        }
    }


}