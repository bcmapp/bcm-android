package com.bcm.messenger.chats.components

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProviders
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.privatechat.AmeConversationViewModel
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.mms.OutgoingExpirationUpdateMessage
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.popup.DataPickerPopupWindow
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.ChatTimestamp
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

object ChatsBurnSetting {
    fun configBurnSetting(activity: FragmentActivity, threadId:Long, recipient: Recipient, masterSecret: MasterSecret, finish: () -> Unit) {
        fun getBurnExpireFromIndex(index: Int): Int {
            return when (index) {
                0 -> 0
                1 -> 30
                2 -> 120
                3 -> 300
                4 -> 3600
                5 -> 86400
                6 -> 604800
                7 -> 1209600
                else -> 0
            }
        }

        val pickView = DataPickerPopupWindow(activity)
                .setTitle(activity.getString(R.string.chats_destroy_message_popup_title))
                .setDataList(listOf(
                        activity.getString(R.string.chats_auto_clear_off),
                        activity.getString(R.string.chats_destroy_message_30_sec),
                        activity.getString(R.string.chats_destroy_message_2_min),
                        activity.getString(R.string.chats_destroy_message_5_min),
                        activity.getString(R.string.chats_destroy_message_1_hour),
                        activity.getString(R.string.chats_destroy_message_1_day),
                        activity.getString(R.string.chats_destroy_message_1_week),
                        activity.getString(R.string.chats_destroy_message_2_weeks)
                ))
                .setCurrentIndex(expireToType(recipient.expireMessages))
                .setCallback { index ->
                    val expire = getBurnExpireFromIndex(index)
                    if (recipient.expireMessages == expire) {
                        AmeDispatcher.mainThread.dispatch {
                            ToastUtil.show(activity, activity.resources.getString(R.string.chats_read_burn_choose_same))
                        }
                        return@setCallback
                    }

                    Observable.create(ObservableOnSubscribe<Boolean> {
                        val goon = if (recipient.expireMessages != expire) {
                            Repository.getRecipientRepo(recipient.address.context())?.setExpireTime(recipient, expire.toLong())
                            true
                        } else {
                            false
                        }
                        it.onNext(goon)
                        it.onComplete()
                    }).subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ result ->
                                if (result) {
                                    ViewModelProviders.of(activity).get(AmeConversationViewModel::class.java).sendMediaMessage(activity,
                                            masterSecret,
                                            OutgoingExpirationUpdateMessage(recipient, ChatTimestamp.getTime(masterSecret.accountContext, threadId), expire * 1000L)) { success ->
                                        if (success) {
                                            finish()
                                        }
                                    }
                                }
                            }, {
                                ALog.e("ChatsBurnSetting", "callBurnAfterRead error", it)
                            })
                }
        pickView.show()
    }

    fun expireToType(expire: Int): Int {
        return when (expire) {
            0 -> 0
            30 -> 1
            120 -> 2
            300 -> 3
            3600 -> 4
            86400 -> 5
            604800 -> 6
            1209600 -> 7
            else -> 0
        }
    }

    fun typeToString(type: Int): String {
        return  when (type) {
            1 -> {
                "30s"
            }
            2 -> {
                "2m"
            }
            3 -> {
                "5m"
            }
            4 -> {
                "1h"
            }
            5 -> {
                "1d"
            }
            6 -> {
                "1w"
            }
            7 -> {
                "2w"
            }
            else -> {
                ""
            }
        }
    }
}