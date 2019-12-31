package com.bcm.messenger.contacts.adapter

import android.content.Context
import androidx.loader.content.AsyncTaskLoader
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.orhanobut.logger.Logger
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.recipients.Recipient
import java.util.*

/**
 * Created by wjh on 2018/4/12
 */
class ContactsListLoader(context: Context, private val isGroup: Boolean = false, private val includeMe: Boolean = false) : AsyncTaskLoader<List<Recipient>>(context) {

    private var mLastResult: List<Recipient>? = null

    override fun loadInBackground(): List<Recipient> {
        val results = ArrayList<Recipient>()
        try {

            ALog.d("ContactsListLoader", "load contacts begin")
            if (isGroup) {
                results.addAll(AmeModuleCenter.group().getJoinedListBySort().map {
                    Recipient.recipientFromNewGroup(context, it)
                })

            } else {
                if (includeMe) {
                    results.addAll(AmeModuleCenter.contact().getContactListWithWait())
                } else {
                    results.addAll(AmeModuleCenter.contact().getContactListWithWait().filter { !it.isSelf })
                }
            }
            ALog.d("ContactsListLoader", "load contacts end")
            mLastResult = results

        } catch (ex: Exception) {
            ALog.e("ContactsListLoader", "run error", ex)
        }

        return results
    }

    /* Runs on the UI thread */
    override fun deliverResult(data: List<Recipient>?) {
        Logger.d("ContactsListLoader deliverResult")
        if (isReset) {
            return
        }

        if (isStarted) {
            super.deliverResult(data)
        }
    }

    override fun onCanceled(data: List<Recipient>?) {
        Logger.d("ContactsListLoader onCanceled")
        ALog.d("ContactsListLoader", "onCanceled")
    }

    override fun onStartLoading() {
        if (takeContentChanged() || mLastResult == null) {
            forceLoad()
        }
    }

    override fun onStopLoading() {
        cancelLoad()
    }

    override fun onReset() {
        super.onReset()
        ALog.d("ContactsListLoader", "onReset")

        // Ensure the loader is stopped
        onStopLoading()
    }

}