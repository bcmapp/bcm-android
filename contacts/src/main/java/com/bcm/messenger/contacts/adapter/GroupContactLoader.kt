package com.bcm.messenger.contacts.adapter

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import androidx.core.os.OperationCanceledException
import androidx.loader.content.AsyncTaskLoader
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.recipients.Recipient
import java.util.*

/**
 * Created by wjh on 2018/8/15
 */
@Deprecated("")
class GroupContactLoader(context: Context) : AsyncTaskLoader<List<Recipient>>(context) {

    companion object {
        const val TAG = "GroupContactLoader"
    }

    private val mObserver: ContentObserver

    init {
        mObserver = object : ContentObserver(Handler()) {
            override fun deliverSelfNotifications(): Boolean {
                return true
            }

            override fun onChange(selfChange: Boolean) {
                onContentChanged()
            }
        }
    }

    override fun loadInBackground(): List<Recipient> {

        ALog.d(TAG, "loadInBackground")
        synchronized(this) {
            if (isLoadInBackgroundCanceled) {
                throw OperationCanceledException()
            }
        }

        val result = mutableListOf<Recipient>()
        try {
            val groupRecipients = Repository.getRecipientRepo()?.getGroupRecipients()
            groupRecipients?.forEach { settings ->
                val recipient = Recipient.fromSnapshot(context, Address.fromSerialized(settings.uid), settings)
                result.add(recipient)
            }
            Collections.sort(result, Recipient.getRecipientComparator())
        } catch (ex: Exception) {
            ALog.e(TAG, "loadInBackground error", ex)
        }

        return result
    }

    /* Runs on the UI thread */
    override fun deliverResult(result: List<Recipient>?) {
        ALog.i(TAG, "deliverResult")
        if (isReset) {
            // An async query came in while the loader is stopped
            return
        }
        if (isStarted) {
            super.deliverResult(result)
        }
    }

    override fun onCanceled(result: List<Recipient>?) {
        ALog.i(TAG, "onCanceled")
//        if (mCursor?.isClosed == false) {
//            mCursor?.close()
//        }
    }

    override fun onStartLoading() {
        ALog.i(TAG, "onStartLoading")
        if (takeContentChanged()) {
            forceLoad()
        }
    }

    override fun onStopLoading() {
        cancelLoad()
    }

    override fun onReset() {
        ALog.i(TAG, "onReset")
        super.onReset()

        // Ensure the loader is stopped
        onStopLoading()
    }

}