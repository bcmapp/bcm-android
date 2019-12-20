package com.bcm.messenger.push

import com.bcm.messenger.utility.storage.SPEditor

class UmengRegisterTimesFix {
    fun fix() {
        SPEditor("umeng_message_state").set("KEY_REGISTER_TIMES", "3")
    }
}