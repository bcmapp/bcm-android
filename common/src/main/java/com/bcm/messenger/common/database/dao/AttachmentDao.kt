package com.bcm.messenger.common.database.dao

import androidx.room.*
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.records.AttachmentRecord

/**
 * Created by Kin on 2019/9/16
 */
@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttachment(attachment: AttachmentDbModel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttachments(attachments: List<AttachmentDbModel>): List<Long>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateAttachment(attachment: AttachmentDbModel): Int

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateAttachments(attachment: List<AttachmentDbModel>)

    @Delete
    fun deleteAttachment(attachment: AttachmentDbModel)

    @Query("DELETE FROM ${AttachmentDbModel.TABLE_NAME} WHERE id = :id")
    fun deleteAttachment(id: Long)

    @Query("DELETE FROM ${AttachmentDbModel.TABLE_NAME}")
    fun deleteAllAttachments()

    // Query SQLs
    @Query("SELECT * FROM ${AttachmentDbModel.TABLE_NAME} WHERE id = :id")
    fun queryAttachment(id: Long): AttachmentRecord?

    @Query("SELECT * FROM ${AttachmentDbModel.TABLE_NAME} WHERE id = :id AND unique_id = :uniqueId")
    fun queryAttachment(id: Long, uniqueId: Long): AttachmentRecord?

    @Query("SELECT * FROM ${AttachmentDbModel.TABLE_NAME} WHERE unique_id = :uniqueId")
    fun queryAttachmentByUniqueId(uniqueId: Long): AttachmentRecord?

    @Query("SELECT * FROM ${AttachmentDbModel.TABLE_NAME} WHERE id in (:idList)")
    fun queryAttachments(idList: List<Long>): List<AttachmentDbModel>

    @Query("SELECT * FROM ${AttachmentDbModel.TABLE_NAME} WHERE mid = :mid")
    fun queryAttachments(mid: Long): List<AttachmentRecord>

    @Query("SELECT * FROM ${AttachmentDbModel.TABLE_NAME} WHERE id != :id AND unique_id != :uniqueId AND data_hash = :hash LIMIT 1")
    fun queryExistAttachment(id: Long, uniqueId: Long, hash: String): AttachmentRecord?

    @Query("SELECT * FROM ${AttachmentDbModel.TABLE_NAME} WHERE data_hash = :hash LIMIT 1")
    fun queryExistAttachment(hash: String): AttachmentRecord?

    @Query("SELECT * FROM ${AttachmentDbModel.TABLE_NAME} WHERE id != :id AND unique_id != :uniqueId AND thumb_hash = :hash LIMIT 1")
    fun queryExistThumbnail(id: Long, uniqueId: Long, hash: String): AttachmentRecord?

    @Query("SELECT * FROM ${AttachmentDbModel.TABLE_NAME} WHERE thumb_hash = :hash LIMIT 1")
    fun queryExistThumbnail(hash: String): AttachmentRecord?

    // Update SQLs
    @Query("UPDATE ${AttachmentDbModel.TABLE_NAME} SET thumbnail_uri = :thumbnailUri, thumb_aspect_ratio = :thumbnailRatio WHERE id = :id")
    fun updateAttachmentThumbnail(id: Long, thumbnailUri: String?, thumbnailRatio: Float): Int

    @Query("UPDATE ${AttachmentDbModel.TABLE_NAME} SET thumbnail_uri = :thumbnailUri, thumb_aspect_ratio = :thumbnailRatio, thumb_hash = :hash, thumb_random = :random WHERE id = :id")
    fun updateAttachmentThumbnail(id: Long, thumbnailUri: String?, thumbnailRatio: Float, hash: String?, random: ByteArray?): Int

    @Query("UPDATE ${AttachmentDbModel.TABLE_NAME} SET transfer_state = :state WHERE id = :id")
    fun updateAttachmentTransferState(id: Long, state: Int)

    @Query("UPDATE ${AttachmentDbModel.TABLE_NAME} SET uri = :dataUri, size = :dataSize, data_hash = :hash, data_random = :random, transfer_state = :state WHERE id = :id")
    fun updateAttachmentData(id: Long, dataUri: String?, dataSize: Long, hash: String?, random: ByteArray?, state: Int): Int

    @Query("UPDATE ${AttachmentDbModel.TABLE_NAME} SET uri = :dataUri, size = :dataSize, transfer_state = :state WHERE id = :id")
    fun updateAttachmentData(id: Long, dataUri: String?, dataSize: Long, state: Int): Int

    @Query("UPDATE ${AttachmentDbModel.TABLE_NAME} SET size = :dataSize, content_type = :contentType WHERE id = :id")
    fun updateAttachmentDataSizeAndType(id: Long, dataSize: Long, contentType: String)

    @Query("UPDATE ${AttachmentDbModel.TABLE_NAME} SET duration = :duration WHERE id = :id")
    fun updateAttachmentDuration(id: Long, duration: Long)

    @Query("UPDATE ${AttachmentDbModel.TABLE_NAME} SET file_name = :fileName WHERE id = :id")
    fun updateAttachmentFileName(id: Long, fileName: String?)
}