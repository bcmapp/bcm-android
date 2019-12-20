package com.bcm.messenger.common.database.dao

import androidx.room.*
import com.bcm.messenger.common.database.model.IdentityDbModel
import com.bcm.messenger.common.database.records.IdentityRecord

/**
 * Created by Kin on 2019/9/16
 */
@Dao
interface IdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIdentity(identity: IdentityDbModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIdentities(identities: List<IdentityDbModel>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun updateIdentity(identity: IdentityDbModel): Long

    @Delete
    fun deleteIdentity(identity: IdentityDbModel)

    @Query("SELECT * FROM ${IdentityDbModel.TABLE_NAME} WHERE id = :id")
    fun queryIdentity(id: Int): IdentityRecord?

    @Query("SELECT * FROM ${IdentityDbModel.TABLE_NAME} WHERE uid = :uid")
    fun queryIdentity(uid: String): IdentityRecord?

    @Query("SELECT * FROM ${IdentityDbModel.TABLE_NAME} WHERE uid = :uid AND `key` = :key")
    fun queryIdentity(uid: String, key: String): IdentityRecord?
}