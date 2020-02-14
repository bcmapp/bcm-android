package com.bcm.messenger.me.ui.login

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment

class RegisterSucceedFragment : AbsRegistrationFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_register_succeed_layout, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        (context as Activity).finish()
    }
}
