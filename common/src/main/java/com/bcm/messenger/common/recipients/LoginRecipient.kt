package com.bcm.messenger.common.recipients

import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.utility.dispatcher.AmeDispatcher

object LoginRecipient:AccountContextMap<Recipient>({
    val recipient = Recipient.alloc(Address.from(it, it.uid))
    AmeDispatcher.io.dispatch {
        AmeModuleCenter.contact(it)?.fetchProfile(it.uid)
    }
    recipient
})