package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.*
import com.bcm.messenger.common.grouprepository.room.entity.NoteRecord

@Dao
interface NoteRecordDao {
    @Query("SELECT * FROM ${NoteRecord.TABLE_NAME} WHERE _id = :topicId")
    fun queryNote(topicId:String): NoteRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveNote(note:NoteRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveNoteList(notes: List<NoteRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun updateNotes(note:NoteRecord)

    @Query("SELECT * FROM ${NoteRecord.TABLE_NAME}")
    fun queryNoteList():List<NoteRecord>

    @Query("DELETE FROM ${NoteRecord.TABLE_NAME} WHERE _id = :topicId")
    fun deleteNote(topicId:String)
}