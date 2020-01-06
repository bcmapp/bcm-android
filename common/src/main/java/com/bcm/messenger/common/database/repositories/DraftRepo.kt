package com.bcm.messenger.common.database.repositories

import android.content.Context
import android.net.Uri
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.R
import com.bcm.messenger.common.database.dao.DraftDao
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.model.DraftDbModel
import com.bcm.messenger.utility.logger.ALog
import java.util.*

/**
 * Created by Kin on 2019/9/17
 */
class DraftRepo(
        private val accountContext: AccountContext,
        private val draftDao: DraftDao
) {


    class Draft(val type: String, val value: String) {
        fun getSnippet(context: Context): String? {
            return when (type) {
                TEXT -> value
                IMAGE -> context.getString(R.string.common_draft_image_snippet)
                VIDEO -> context.getString(R.string.common_draft_video_snippet)
                AUDIO -> context.getString(R.string.common_draft_audio_snippet)
                LOCATION -> context.getString(R.string.common_draft_location_snippet)
                else -> null
            }
        }

        companion object {
            const val TEXT = "text"
            const val IMAGE = "image"
            const val VIDEO = "video"
            const val AUDIO = "audio"
            const val LOCATION = "location"
        }
    }

    class Drafts : LinkedList<Draft>() {
        private fun getDraftOfType(type: String): Draft? {
            for (draft in this) {
                if (type == draft.type) {
                    return draft
                }
            }
            return null
        }

        fun getSnippet(context: Context): String? {
            val textDraft = getDraftOfType(Draft.TEXT)
            return if (textDraft != null) {
                textDraft.getSnippet(context)
            } else if (size > 0) {
                get(0).getSnippet(context)
            } else {
                ""
            }
        }

        fun getUriSnippet(context: Context): Uri? {
            val imageDraft = getDraftOfType(Draft.IMAGE)

            return if (imageDraft != null && imageDraft.value.isNotEmpty()) {
                Uri.parse(imageDraft.value)
            } else null

        }
    }

    private val TAG = "DraftRepo"

    fun insertDrafts(threadId: Long, drafts: List<Draft>) {
        UserDatabase.getDatabase(accountContext).runInTransaction {
            ALog.i(TAG, "insertDrafts threadId: $threadId, drafts: ${drafts.size}")
            val modelList = mutableListOf<DraftDbModel>()
            drafts.forEach {
                val model = DraftDbModel()
                model.threadId = threadId
                model.type = it.type
                model.value = it.value

                modelList.add(model)
            }
            draftDao.deleteDraft(threadId)
            draftDao.insertDrafts(modelList)
        }

    }

    fun clearDrafts(threadId: Long) {
        ALog.i(TAG, "clearDrafts threadId: $threadId")
        draftDao.deleteDraft(threadId)
    }

    fun clearDrafts(threadIds: List<Long>) {
        draftDao.deleteDrafts(threadIds)
    }

    fun clearAllDrafts() {
        draftDao.deleteAllDrafts()
    }

    fun getDrafts(threadId: Long): Drafts {
        val models = draftDao.queryDrafts(threadId)
        val drafts = Drafts()
        models.forEach {
            val draft = Draft(it.type, it.value)
            drafts.add(draft)
        }
        return drafts
    }
}