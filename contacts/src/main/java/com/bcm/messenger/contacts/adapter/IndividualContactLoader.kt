package com.bcm.messenger.contacts.adapter

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import androidx.loader.content.AsyncTaskLoader
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.contacts.logic.BcmContactLogic
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.recipients.Recipient

/**
 * Created by wjh on 2018/8/31
 */
@Deprecated("")
class IndividualContactLoader(context: Context) : AsyncTaskLoader<IndividualContactLoader.IndividualContact>(context) {

    private val TAG = "IndividualContact"
    private var mCursor: Cursor? = null
    private val mObserver: ContentObserver
    private var mLastIndividualContact: IndividualContact? = null
        @Synchronized set
        @Synchronized get

    init {
        mObserver = object : ContentObserver(Handler()) {
            override fun deliverSelfNotifications(): Boolean {
                return true
            }

            override fun onChange(selfChange: Boolean) {
                ALog.d(TAG, "observer notify change: $isStarted")
                if (mLastIndividualContact != null) {
                    onContentChanged()
                }
            }
        }
    }

    override fun loadInBackground(): IndividualContact? {
        ALog.i(TAG, "loadInBackground begin")
        if(mCursor?.isClosed == false) {
            mCursor?.close()
            mCursor = null
        }

        var cursor: Cursor? = null

        val bcmList = mutableListOf<Recipient>()
        try {
            val friendSettings = Repository.getRecipientRepo()?.getFriendsFromContact()
            friendSettings?.forEach { settings ->
                val recipient = Recipient.fromSnapshot(context, Address.fromSerialized(settings.uid), settings)
                bcmList.add(recipient)
            }

            val comparator = Recipient.getRecipientComparator()
            BcmContactLogic.contactFinder.updateContact(bcmList, comparator)

            if (isLoadInBackgroundCanceled) {
                return mLastIndividualContact
            }
            mLastIndividualContact = IndividualContact(BcmContactLogic.contactFinder.getContactList(), listOf(), listOf())
            ALog.i(TAG, "loadInBackground end, bcmList: ${bcmList.size}")

        } catch (ex: Exception) {
            ALog.e(TAG, "loadInBackground error", ex)
            cursor?.close()
            mCursor = null
        }
        return mLastIndividualContact
    }

    /* Runs on the UI thread */
    override fun deliverResult(result: IndividualContact?) {
        ALog.i(TAG, "deliverResult")
        if (isReset) {
            // An async query came in while the loader is stopped
            if (mCursor?.isClosed == false) {
                mCursor?.close()
            }
            return
        }
        if (isStarted) {
            super.deliverResult(result)
        }
    }

    override fun onCanceled(result: IndividualContact?) {
        ALog.i(TAG, "onCanceled")
    }

    override fun onStartLoading() {
        ALog.i(TAG, "onStartLoading")
        if (takeContentChanged() || mCursor == null) {
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

        if (mCursor?.isClosed == false) {
            mCursor?.close()
        }
        mCursor = null
    }

    class IndividualContact(val friendList: List<Recipient>, val phoneList: List<Recipient>, val inviteList: List<Recipient>) {}
}

