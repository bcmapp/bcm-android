package com.bcm.messenger.common.provider

import com.bcm.route.api.IRouteProvider
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.recipients.Recipient

/**
 * Created by Kin on 2019/6/14
 */
interface IForwardSelectProvider : IRouteProvider {

    interface ForwardSelectCallback {
        fun onClickContact(recipient: Recipient)
    }

    fun setCallback(callback: ForwardSelectCallback)
    fun setContactSelectContainer(layoutId: Int)
    fun setGroupSelectContainer(layoutId: Int)
}