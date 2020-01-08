package com.bcm.messenger.me.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_fragment_pick_nickname_layout.*
import org.whispersystems.libsignal.ecc.ECKeyPair

class PickNicknameFragment : AbsRegistrationFragment() {

    private var nonce: Long = 0L
    private var keyPair: ECKeyPair? = null
    private var password: String? = null
    private var passwordHint = ""

    fun initParams(nonce:Long, keyPair: ECKeyPair, password: String, passwordHint: String) {
        this.nonce = nonce
        this.keyPair = keyPair
        this.password = password
        this.passwordHint = passwordHint
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_pick_nickname_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nick_done_button.setOnClickListener {
            AmePopup.loading.show(activity, false)
            val keyPair = this.keyPair
            val password = this.password
            if (null == keyPair || nonce == 0L || null == password){
                AmePopup.loading.dismiss()
                return@setOnClickListener
            }

            val name = new_nickname.text.toString().trim()
            if (name.isEmpty()){
                AmePopup.result.failure(activity, "nickname can't empty")
                AmePopup.loading.dismiss()
                return@setOnClickListener
            }

            nick_done_button.isEnabled = false
            AmeLoginLogic.register(nonce, keyPair, password, passwordHint, name) {
                if (it) {
                    AmePopup.loading.dismiss()

                    val uid = BCMPrivateKeyUtils.provideUid(keyPair.publicKey.serialize())
                    val accountContext = AmeModuleCenter.login().getAccountContext(uid)
                    val f = RegisterSucceedFragment()
                    activity?.supportFragmentManager?.beginTransaction()
                            ?.replace(R.id.register_container, f)
                            ?.commitAllowingStateLoss()

                    new_nickname.postDelayed({
                        BcmRouter.getInstance().get(ARouterConstants.Activity.APP_HOME_PATH)
                                .putBoolean(ARouterConstants.PARAM.PARAM_LOGIN_FROM_REGISTER, true)
                                .putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
                                .navigation(activity)
                        activity?.finish()
                    }, 2000)

                }
                else{
                    AmePopup.loading.dismiss()
                    AmePopup.result.failure(activity, getString(R.string.me_text_register_failed))
                    AmeDispatcher.mainThread.dispatch({
                        BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                .navigation(activity)
                    },1500)
                }
            }
        }
    }

}
