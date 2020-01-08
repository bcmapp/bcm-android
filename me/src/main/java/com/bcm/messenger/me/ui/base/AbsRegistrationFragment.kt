package com.bcm.messenger.me.ui.base

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.route.api.BcmRouter

/**
 * ling created in 2018/6/7
 **/
abstract class AbsRegistrationFragment : Fragment() {

    companion object {
        fun showAnimatorForView(view: View?) {
            view ?: return
            val animX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.8f, 1f)
            val animY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.8f, 1f)
            val animatorSet = AnimatorSet()
            animatorSet.duration = 1000
            animatorSet.playTogether(animX, animY)
            animatorSet.start()
        }
    }

    protected fun gotoHomeActivity(accountContext: AccountContext, register: Boolean) {
        activity?.apply {
            BcmRouter.getInstance().get(ARouterConstants.Activity.APP_HOME_PATH)
                    .putBoolean(ARouterConstants.PARAM.PARAM_LOGIN_FROM_REGISTER, register)
                    .putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
                    .navigation(this)
        
            AmeDispatcher.mainThread.dispatch({
                finish()
            }, 1000)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.hideKeyboard()
    }
}