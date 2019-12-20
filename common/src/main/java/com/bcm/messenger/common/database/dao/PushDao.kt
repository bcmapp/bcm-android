package com.bcm.messenger.common.database.dao

import androidx.room.*
import com.bcm.messenger.common.database.model.PushDbModel

/**
 * Created by Kin on 2019/9/16
 */
@Dao
interface PushDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPushMessage(pushMessage: PushDbModel): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updatePushMessage(pushMessage: PushDbModel)

    @Delete
    fun deletePushMessage(pushMessage: PushDbModel)

    @Query("DELETE FROM ${PushDbModel.TABLE_NAME} WHERE id = :id")
    fun deletePushMessage(id: Long)

    @Query("SELECT * FROM ${PushDbModel.TABLE_NAME} WHERE id = :id")
    fun queryPushMessage(id: Long): PushDbModel?

    @Query("""
        SELECT * FROM ${PushDbModel.TABLE_NAME} WHERE type = :type AND source_uid = :sourceUid AND device_id = :deviceId AND
        content = :content AND legacy_msg = :legacyMessage AND timestamp = :timestamp
    """)
    fun queryPushMessage(type: Int, sourceUid: String, deviceId: Int, content: String, legacyMessage: String, timestamp: Long): PushDbModel?
}