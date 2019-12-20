package com.bcm.messenger.common.database.dao

import androidx.room.*
import com.bcm.messenger.common.database.model.DraftDbModel

/**
 * Created by Kin on 2019/9/16
 */
@Dao
interface DraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDraft(draft: DraftDbModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDrafts(drafts: List<DraftDbModel>)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateDraft(draft: DraftDbModel)

    @Query("DELETE FROM ${DraftDbModel.TABLE_NAME} WHERE thread_id = :threadId")
    fun deleteDraft(threadId: Long)

    @Query("DELETE FROM ${DraftDbModel.TABLE_NAME} WHERE thread_id in (:threadIdList)")
    fun deleteDrafts(threadIdList: List<Long>)

    @Query("DELETE FROM ${DraftDbModel.TABLE_NAME}")
    fun deleteAllDrafts()

    @Query("SELECT * FROM ${DraftDbModel.TABLE_NAME} WHERE thread_id = :threadId")
    fun queryDrafts(threadId: Long): List<DraftDbModel>
}