package com.bcm.messenger.contacts

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.event.FriendRequestEvent
import com.bcm.messenger.common.event.HomeTabEvent
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.Sidebar
import com.bcm.messenger.common.ui.StickyLinearDecoration
import com.bcm.messenger.common.utils.ConversationUtils
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.contacts.adapter.ContactsLinearDecorationCallback
import com.bcm.messenger.contacts.adapter.IndividualContactAdapter
import com.bcm.messenger.contacts.logic.BcmContactLogic
import com.bcm.messenger.contacts.provider.ContactModuleImp
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.contacts_fragment_individual.*

/**
 * Created by wjh on 2018/2/26.
 */
@Route(routePath = ARouterConstants.Fragment.CONTACTS_INDIVIDUAL)
class IndividualContactFragment : BaseFragment() {

    private val TAG = "IndividualContactFragment"

    private lateinit var mAdapter: IndividualContactAdapter

    override fun onResume() {
        super.onResume()
        ALog.d(TAG, "onResume")
        checkUnhandledRequest()
    }

    override fun onDestroy() {
        super.onDestroy()
        ALog.d(TAG, "onDestroy")
        RxBus.unSubscribe(TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        contacts_list?.removeItemDecorationAt(0)
        contacts_list?.removeAllViews()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.contacts_fragment_individual, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ALog.d(TAG, "onViewCreated")
        mAdapter = IndividualContactAdapter(context
                ?: return, object : IndividualContactAdapter.OnContactActionListener {

            override fun onSearch() {
                ContactModuleImp().openSearch(context ?: return)
            }

            override fun onEmpty() {
                BcmContactLogic.checkAndSync()
            }

            override fun onSelect(recipient: Recipient) {
                activity?.hideKeyboard()
                ConversationUtils.getExistThreadId(recipient) {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.CHAT_CONVERSATION_PATH)
                            .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
                            .putLong(ARouterConstants.PARAM.PARAM_THREAD, it)
                            .navigation()
                }
            }

            override fun onRequest() {
                RxBus.post(HomeTabEvent(HomeTabEvent.TAB_CONTACT, showFigure = 0))
                startActivity(Intent(context, FriendRequestsListActivity::class.java))
            }

            override fun onGroupContact() {
                startActivity(Intent(context, GroupContactActivity::class.java))
            }
        })

        contacts_list.layoutManager = LinearLayoutManager(context)
        contacts_list.adapter = mAdapter
        contacts_list.addItemDecoration(StickyLinearDecoration(ContactsLinearDecorationCallback(context ?: AppContextHolder.APP_CONTEXT, mAdapter)))

        contacts_sidebar.setLetterList(Recipient.LETTERS.toList())
        contacts_sidebar.setOnTouchingLetterChangedListener(object : Sidebar.OnTouchingLetterChangedListener {

            override fun onLetterChanged(letter: String): Int {
                return mAdapter.findSidePosition(letter)
            }

            override fun onLetterScroll(position: Int) {
                if (position in 0 until mAdapter.itemCount) {
                    val layoutManager = contacts_list.layoutManager as LinearLayoutManager
                    layoutManager.scrollToPositionWithOffset(position, 0)
                }
            }
        })
        contacts_sidebar.setFastScrollHelper(contacts_list, mAdapter)

        mAdapter.showLoading(true)

        Observable.create<List<Recipient>> {
            it.onNext(BcmContactLogic.contactFinder.getContactList())
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateContactList(it)
                }, {
                    ALog.e(TAG, "init getContactList error", it)
                })

        BcmContactLogic.contactLiveData.observe(this, Observer { data ->
            ALog.i(TAG, "onContactListLoaded data before: ${mAdapter.itemCount}, is main thread: ${Looper.myLooper() == Looper.getMainLooper()}")
            updateContactList(data)
            ALog.i(TAG, "onContactListLoaded data after: ${mAdapter.itemCount}, is main thread: ${Looper.myLooper() == Looper.getMainLooper()}")

        })

        RxBus.subscribe<FriendRequestEvent>(TAG) {
            checkUnhandledRequest(it.unreadCount)
        }
    }

    private fun updateContactList(data: List<Recipient>) {
        ALog.d(TAG, "updateContactList data: ${data.size}")
        mAdapter.setContacts(data)

        if (mAdapter.getTrueDataList().isEmpty()) {
            contacts_sidebar?.hide()
        }

        contacts_list?.postDelayed({
            checkScrollSituation(contacts_list?.layoutManager as? LinearLayoutManager ?: return@postDelayed)
        }, 100)

    }

    private fun selectLetter(position: Int) {
        try {
            val letter = mAdapter.findSideLetter(position)
            contacts_sidebar?.selectLetter(letter)
        }catch (ex: Exception) {
            ALog.e(TAG, "selectLetter position: $position error", ex)
        }
    }

    private fun checkScrollSituation(layoutManager: LinearLayoutManager) {
        val pos = layoutManager.findFirstVisibleItemPosition()
        if (pos >= 0 && pos < mAdapter.itemCount) {
            selectLetter(pos)
        }
    }

    private fun checkUnhandledRequest(unreadCount: Int = 0) {
        Observable.create<Pair<Int, Int>> {
            val requestDao = UserDatabase.getDatabase().friendRequestDao()
            var unread = unreadCount
            if (unread == 0) {
                unread = requestDao.queryUnreadCount()
            }
            it.onNext(Pair(requestDao.queryUnhandledCount(), unread))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    mAdapter.updateFriendRequest(it.first, it.second)
                }
    }
}
