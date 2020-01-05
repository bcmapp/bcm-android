package com.bcm.messenger.contacts

import android.os.Bundle
import android.view.View
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriendRequest
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.contacts_activity_handle_friend_request.*

/**
 * Created by Kin on 2019/5/17
 */
class FriendRequestHandleActivity : SwipeBaseActivity(), RecipientModifiedListener {
    private val TAG = "FriendRequestHandleActivity"

    private lateinit var request: BcmFriendRequest
    private lateinit var recipient: Recipient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contacts_activity_handle_friend_request)


        initView()
        getRequest()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }
    }

    private fun initView() {
        handle_req_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        handle_req_accept.setOnClickListener {
            sendReply(true)
        }
        handle_req_decline.setOnClickListener {
            AmePopup.bottom.newBuilder()
                    .withTitle(getString(R.string.contacts_friend_decline_title, recipient.name))
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.contacts_friend_decline), getColorCompat(R.color.common_color_ff3737)) {
                        sendReply(false)
                    })
                    .withCancelable(true)
                    .withDoneTitle(getString(R.string.common_cancel))
                    .show(this)
        }
    }

    private fun sendReply(approve: Boolean) {
        ALog.i(TAG, "Start to send reply approve $approve")
        AmePopup.loading.show(this)
        AmeModuleCenter.contact(getAccountContext())?.replyFriend(recipient.address.serialize(), approve, request) {
            AmePopup.loading.dismiss()
            if (it) {
                RxBus.post(HANDLED_REQ, Pair(request.id ?: 0L, true))
                if (approve) {
                    AmePopup.result.succeed(this, getString(R.string.contacts_add_friend_success)) {
                        finish()
                    }
                } else {
                    finish()
                }
            } else {
                AmePopup.result.failure(this, getString(if (approve) R.string.contacts_add_friend_failed else R.string.contacts_add_friend_reject_failed))
            }
        }

    }

    private fun initData() {
        recipient = Recipient.from(getAccountContext(), Address.fromSerialized(request.proposer), true)
        recipient.addListener(this)

        handle_req_avatar.setPhoto(getAccountContext(), recipient)
        handle_req_name.text = recipient.name

        if (request.memo.isNotEmpty()) {
            handle_req_detail.text = request.memo
        } else {
            handle_req_detail.visibility = View.GONE
            handle_req_arrow.visibility = View.GONE
        }
    }

    private fun getRequest() {
        Observable.create<BcmFriendRequest> {
            val id = intent.getLongExtra("id", -1L)
            if (id != -1L) {
                it.onNext(UserDatabase.getDatabase(getAccountContext()).friendRequestDao().query(id))
                ALog.logForSecret(TAG, "Get id = $id request")
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    request = it
                    initData()
                }, {
                    ALog.e(TAG, "Get request error", it)
                })
    }

    override fun onModified(recipient: Recipient) {
        this.recipient = recipient

        handle_req_avatar.setPhoto(getAccountContext(), recipient)
        handle_req_name.text = recipient.name
    }
}