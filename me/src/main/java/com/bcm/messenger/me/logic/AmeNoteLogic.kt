package com.bcm.messenger.me.logic

import android.annotation.SuppressLint
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.grouprepository.room.dao.NoteRecordDao
import com.bcm.messenger.common.grouprepository.room.entity.NoteRecord
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.bean.BcmNote
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.greenrobot.eventbus.EventBus
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.util.Util
import java.io.*
import java.security.MessageDigest

class AmeNoteLogic : AppForeground.IForegroundEvent {

    companion object {
        private const val TAG = "AmeNoteLogic"
        private const val NOTE_LOCAL_DIR = "/notes"
        private const val MAC_KEY_LEN = 32
        private const val KEY_LEN = 32
        private const val HASH_LEN = 32 //hash256

        private val sInstance: AmeNoteLogic by lazy {
            AmeNoteLogic()
        }

        fun getInstance(): AmeNoteLogic {
            return sInstance
        }
    }

    private val notesList = ArrayList<BcmNote>()
    private var uid: String = ""
    private var locked = true

    init {
        AppForeground.listener.addListener(this)
    }

    fun updateUser(uid: String) {
        if (this.uid == uid) {
            return
        }

        locked = true
        this.uid = uid
        if (this.uid.isBlank()) {
            notesList.clear()
        } else {
            loadCache()
        }
    }

    fun refreshCurrentUser() {
        locked = true
        loadCache()
    }

    fun isLocked(): Boolean {
        if (notesList.isEmpty()) {
            locked = false
            return false
        }
        return locked
    }

    fun lock() {
        if (notesList.isNotEmpty()) {
            locked = true
        }
    }

    fun unlock(pwd: String, result: (succeed: Boolean) -> Unit) {
        AmeLoginLogic.checkPassword(pwd) {
            if (it) {
                locked = false
            }
            result(it)
        }
    }

    @SuppressLint("CheckResult")
    private fun loadCache() {
        if (!AMELogin.isLogin) {
            return
        }

        notesList.clear()

        Observable.create<List<BcmNote>> { em ->
            val noteList = getDao().queryNoteList()
            val list = noteList.map {
                val topic = if (it.topic.isBlank()) {
                    it.defaultTopic
                } else {
                    it.topic
                }
                BcmNote(it.topicId, decodeTopic(topic), it.author, it.pin, it.editPosition, it.timestamp)
            }

            val validList = list.filter { it.topic.isNotEmpty() }
            if (validList.size != list.size) {
                ALog.e(TAG, "cache failed ${list.size - validList.size}")
            }
            em.onNext(sortNoteList(validList))
            em.onComplete()
        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    ALog.e(TAG, "init", it)
                }.subscribe {
                    notesList.addAll(it)
                    EventBus.getDefault().post(NoteListChangedEvent())
                }
    }

    fun addNote(topic: String, noteContent: String, editPosition: Int, result: (succeed: Boolean, topicId: String, error: String) -> Unit) {
        if (topic.isBlank() && noteContent.isBlank()) {
            return
        }

        AmeDispatcher.io.dispatch {
            val note = BcmNote(genNoteTopicId())
            if (topic.isNotBlank()) {
                note.topic = topic
            } else {
                note.topic = topicFromContent(noteContent)
            }

            note.timestamp = AmeTimeUtil.serverTimeMillis()
            note.author = ""
            note.lastEditPosition = editPosition


            val dbNote = getDao().queryNote(note.topicId) ?: NoteRecord()
            dbNote.topicId = note.topicId
            dbNote.topic = encodeTopic(topic)
            dbNote.defaultTopic = encodeTopic(note.topic)
            dbNote.timestamp = note.timestamp
            dbNote.editPosition = editPosition

            if (noteContent.isNotBlank()) {
                if (!saveToFile(note.topicId, noteContent, dbNote)) {
                    ALog.i(TAG, "addNote save to file failed")
                }
            }

            getDao().saveNote(dbNote)

            ALog.i(TAG, "new note ${note.topicId}")
            AmeDispatcher.mainThread.dispatch {
                notesList.add(note)
                resortNoteList()
                EventBus.getDefault().post(NoteListChangedEvent())
                result(true, note.topicId, "")
            }
        }
    }

    private fun resortNoteList() {
        val newList = sortNoteList(notesList)
        notesList.clear()
        notesList.addAll(newList)
    }

    private fun sortNoteList(list: List<BcmNote>): List<BcmNote> {
        return list.sortedWith(Comparator { o1, o2 ->
            if (o1.pin && !o2.pin) {
                return@Comparator -1
            } else if (!o1.pin && o2.pin) {
                return@Comparator 1
            } else {
                return@Comparator when {
                    o1.timestamp > o2.timestamp -> -1
                    o1.timestamp == o2.timestamp -> 0
                    else -> 1
                }
            }
        })
    }

    fun updateTopic(topicId: String, topic: String, result: (succeed: Boolean, error: String) -> Unit) {
        val note = findNote(topicId)
        if (null != note) {
            AmeDispatcher.io.dispatch {
                val dbNote = getDao().queryNote(note.topicId) ?: return@dispatch

                val newTopic = if (topic.isBlank()) {
                    topicFromContent(loadFromFile(topicId, dbNote))
                } else {
                    topic
                }
                if (newTopic.isBlank()) {
                    return@dispatch
                }

                note.topic = newTopic
                note.timestamp = AmeTimeUtil.serverTimeMillis()

                dbNote.topic = topic
                dbNote.defaultTopic = newTopic
                dbNote.timestamp = note.timestamp

                getDao().saveNote(dbNote)

                AmeDispatcher.mainThread.dispatch {
                    resortNoteList()
                    EventBus.getDefault().post(NoteListChangedEvent())
                    result(true, "")
                }
            }
        }
    }

    fun updateNote(topicId: String, noteContent: String, editPosition: Int, result: (succeed: Boolean, error: String) -> Unit) {
        val note = findNote(topicId)
        if (null != note && noteContent.isNotBlank()) {
            note.lastEditPosition = editPosition
            note.timestamp = AmeTimeUtil.serverTimeMillis()
            AmeDispatcher.io.dispatch {
                val dbNote = getDao().queryNote(topicId) ?: return@dispatch
                dbNote.timestamp = note.timestamp
                if (dbNote.topic.isBlank()) {
                    note.topic = topicFromContent(noteContent)
                    dbNote.defaultTopic = encodeTopic(note.topic)
                }

                dbNote.editPosition = editPosition

                saveToFile(topicId, noteContent, dbNote)

                getDao().saveNote(dbNote)
                AmeDispatcher.mainThread.dispatch {
                    resortNoteList()
                    EventBus.getDefault().post(NoteListChangedEvent())
                    result(true, "")
                }
            }
        }
    }

    fun deleteNote(topicId: String, result: (succeed: Boolean, error: String) -> Unit) {
        val note = findNote(topicId)
        if (note != null) {
            notesList.remove(note)
            AmeDispatcher.io.dispatch {
                getDao().deleteNote(topicId)
                deleteFile(topicId)
                AmeDispatcher.mainThread.dispatch {
                    EventBus.getDefault().post(NoteListChangedEvent())
                    result(true, "")
                }
            }
        } else {
            result(true, "")
        }
    }

    fun pinNote(topicId: String, pin: Boolean) {
        val note = findNote(topicId)
        if (null != note) {
            note.pin = pin
            AmeDispatcher.io.dispatch {
                val dbNote = getDao().queryNote(topicId) ?: return@dispatch
                dbNote.pin = pin

                getDao().saveNote(dbNote)
                AmeDispatcher.mainThread.dispatch {
                    resortNoteList()
                    EventBus.getDefault().post(NoteListChangedEvent())
                }
            }
        }
    }

    fun getNote(topicId: String): BcmNote? {
        return findNote(topicId)
    }

    fun loadNoteContent(topicId: String, result: (note: String) -> Unit) {
        AmeDispatcher.io.dispatch {
            val dbNote = getDao().queryNote(topicId)
            if (null != dbNote) {
                val note = loadFromFile(topicId, dbNote)
                AmeDispatcher.mainThread.dispatch {
                    result(note)
                }
            }
        }
    }

    fun getNoteList(): List<BcmNote> {
        return notesList
    }

    private fun findNote(topicId: String): BcmNote? {
        for (i in notesList) {
            if (i.topicId == topicId) {
                return i
            }
        }
        return null
    }

    private fun topicFromContent(noteContent: String): String {
        try {
            val reader = BufferedReader(StringReader(noteContent))
            while (true) {
                val line = reader.readLine()
                if (line.isNotBlank()) {
                    return line
                }
            }
        } catch (e: Exception) {
            ALog.e(TAG, "topicFromContent failed", e)
        }

        return ""
    }

    private fun genNoteTopicId():String {
        return Base64.encodeBytes(BCMPrivateKeyUtils.getSecretBytes(16), Base64.URL_SAFE)
    }

    private fun deleteFile(topicId: String) {
        try {
            val file = File(noteStorePath(), topicId)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "delete file", e)
        }

    }

    private fun saveToFile(topicId: String, noteContent: String, dbNote: NoteRecord): Boolean {
        var output: OutputStream? = null
        try {
            val dir = File(noteStorePath())
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val file = File(noteStorePath(), topicId)
            if (!file.exists()) {
                file.createNewFile()
            }

            val key = if (dbNote.key.isNotEmpty()) {
                EncryptUtils.base64Decode(dbNote.key.toByteArray())
            } else {
                val bytes = BCMPrivateKeyUtils.getSecretBytes(MAC_KEY_LEN + KEY_LEN)
                dbNote.key = String(EncryptUtils.base64Encode(bytes))
                bytes
            }

            val outputFactory = AttachmentCipherOutputStreamFactory(key)

            output = FileOutputStream(file)

            val cipherOutput = outputFactory.createFor(output)

            cipherOutput.write(noteContent.toByteArray())
            cipherOutput.flush()

            dbNote.digest = String(EncryptUtils.base64Encode(cipherOutput.transmittedDigest))

            output.close()
            output = null
            return true
        } catch (e: Throwable) {
            ALog.e(TAG, "saveToFile", e)

            return false
        } finally {
            try {
                output?.close()
            } catch (e: Throwable) {
            }
        }
    }

    private fun loadFromFile(topicId: String, dbNote: NoteRecord): String {
        var input: InputStream? = null

        try {
            val dir = File(noteStorePath())
            if (!dir.exists()) {
                return ""
            }

            val file = File(noteStorePath(), topicId)
            if (!file.exists()) {
                ALog.e(TAG, "loadFromFile content not exist")
                return ""
            }

            val digest = dbNote.digest
            if (digest.isNullOrEmpty()) {
                ALog.e(TAG, "loadFromFile wrong digest length")
                return ""
            }

            if (dbNote.key.isEmpty()) {
                ALog.e(TAG, "loadFromFile wrong key length")
                return ""
            }

            val key = EncryptUtils.base64Decode(dbNote.key.toByteArray())
            if (key.size != MAC_KEY_LEN + KEY_LEN) {
                ALog.e(TAG, "loadFromFile wrong key length 1")
                return ""
            }

            input = AttachmentCipherInputStream.createFor(file, 0, key, EncryptUtils.base64Decode(digest.toByteArray()))

            val content = String(input.readBytes())

            input.close()
            input = null
            return content
        } catch (e: Throwable) {
            ALog.e(TAG, "loadFromFile", e)

            return ""
        } finally {
            try {
                input?.close()
            } catch (e: Throwable) {
            }
        }
    }

    private fun noteStorePath(): String {
        return AMELogin.accountDir + NOTE_LOCAL_DIR
    }

    private fun getDao(): NoteRecordDao {
        return UserDatabase.getDatabase().noteRecordDao()
    }

    private fun encodeTopic(topic: String): String {
        if (topic.isEmpty()) {
            return ""
        }

        try {
            val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(AppContextHolder.APP_CONTEXT)

            val bytes = EncryptUtils.aes256EncryptAndBase64(topic, myKeyPair.privateKey.serialize())

            val output = ByteArrayOutputStream()
            output.write(EncryptUtils.computeSHA256(topic.toByteArray()))
            output.write(EncryptUtils.base64Decode(bytes.toByteArray()))
            return String(EncryptUtils.base64Encode(output.toByteArray()))
        } catch (e: Throwable) {
            ALog.e(TAG, "decodeTopic", e)
        }

        return ""
    }

    private fun decodeTopic(encryptedTopic: String): String {
        if (encryptedTopic.isEmpty()) {
            return ""
        }

        try {
            val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(AppContextHolder.APP_CONTEXT)
            val bytes = EncryptUtils.base64Decode(encryptedTopic.toByteArray())

            if (bytes.size <= HASH_LEN) {
                return ""
            }

            val parts = Util.split(bytes, 32, bytes.size - 32)

            val topic = EncryptUtils.aes256DecryptAndBase64(String(EncryptUtils.base64Encode(parts[1])), myKeyPair.privateKey.serialize())
            if (MessageDigest.isEqual(EncryptUtils.computeSHA256(topic.toByteArray()), parts[0])) {
                return topic
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "decodeTopic", e)
        }

        return ""
    }


    override fun onForegroundChanged(isForeground: Boolean) {
        if (isForeground) {
            if (!locked) {
                locked = true
            }
        }
    }

    class NoteListChangedEvent() : NotGuard
}