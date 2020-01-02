package com.bcm.messenger.common.database.migrate

import com.bcm.messenger.common.AccountContext

/**
 * Created by Kin on 2019/10/28
 */
interface IDatabaseMigration {
    fun doMigrate(accountContext: AccountContext, callback: (finishCount: Int) -> Unit)
    fun clearFlag()
}