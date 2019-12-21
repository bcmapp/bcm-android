package com.bcm.messenger.common.database.records

import com.bcm.messenger.common.database.model.RecipientDbModel
import com.bcm.messenger.utility.proguard.NotGuard

/**
 * Created by Kin on 2019/9/26
 */
open class RecipientModel : RecipientDbModel(), NotGuard {
    var identityKey: String? = null
}