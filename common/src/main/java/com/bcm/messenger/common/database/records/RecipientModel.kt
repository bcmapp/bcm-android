package com.bcm.messenger.common.database.records

import com.bcm.messenger.common.database.model.RecipientDbModel

/**
 * Created by Kin on 2019/9/26
 */
open class RecipientModel : RecipientDbModel() {
    var identityKey: String? = null
}