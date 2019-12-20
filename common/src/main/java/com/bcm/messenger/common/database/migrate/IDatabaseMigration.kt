package com.bcm.messenger.common.database.migrate

/**
 * Created by Kin on 2019/10/28
 */
interface IDatabaseMigration {
    fun doMigrate(callback: (finishCount: Int) -> Unit)
    fun clearFlag()
}