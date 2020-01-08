package com.bcm.messenger.chats.forward

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.IForwardSelectProvider
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.RecipientAvatarView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_fragment_forward_recent.*

@Route(routePath = ARouterConstants.Fragment.FORWARD_FRAGMENT)
class ForwardRecentFragment : BaseFragment(), IForwardSelectProvider {
    private val TAG = "ForwardRecentFragment"

    private lateinit var headerView: ForwardHeaderView
    private val threadList = mutableListOf<Recipient>()
    private var callback: IForwardSelectProvider.ForwardSelectCallback? = null
    private var groupContainerId: Int = 0
    private var privateContainerId: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_forward_recent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recent_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                activity?.finish()
            }
        })

        val act = activity ?: return

        headerView = ForwardHeaderView(act)
        headerView.setCallback(object : ForwardHeaderView.ForwardHeaderViewCallback {
            override fun onSearchResult(filter: String, result: List<Recipient>) {
                if (filter.isNotBlank()) {
                    (recent_recyclerview.adapter as RecentAdapter).setDataList(result)
                } else {
                    (recent_recyclerview.adapter as RecentAdapter).setDataList(threadList)
                }
            }

            override fun onContactsClicked() {
                if (activity?.isFinishing == false) {
                    showContactSelectFragment(privateContainerId)
                }
            }

            override fun onGroupClicked() {
                if (activity?.isFinishing == false) {
                    showGroupSelectFragment(groupContainerId)
                }
            }
        })

        initRecyclerView()
    }

    override fun setCallback(callback: IForwardSelectProvider.ForwardSelectCallback) {
        this.callback = callback
    }

    override fun setContactSelectContainer(layoutId: Int) {
        privateContainerId = layoutId
    }

    override fun setGroupSelectContainer(layoutId: Int) {
        groupContainerId = layoutId
    }

    private fun showContactSelectFragment(layoutId: Int) {
        val fragment = ForwardContactFragment()
        fragment.arguments = Bundle().apply {
            putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
        }
        fragment.setCallback(callback)
        fragmentManager?.beginTransaction()
                ?.setCustomAnimations(R.anim.common_slide_from_right, R.anim.common_slide_to_right, R.anim.common_slide_from_right, R.anim.common_slide_to_right)
                ?.add(layoutId, fragment)
                ?.addToBackStack("Contact")
                ?.commit()
    }

    private fun showGroupSelectFragment(layoutId: Int) {
        val fragment = ForwardContactFragment()
        fragment.arguments = Bundle().apply {
            putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_CONTACT_GROUP, true)
            putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
        }
        fragment.setCallback(callback)
        fragmentManager?.beginTransaction()
                ?.setCustomAnimations(R.anim.common_slide_from_right, R.anim.common_slide_to_right, R.anim.common_slide_from_right, R.anim.common_slide_to_right)
                ?.add(layoutId, fragment)
                ?.addToBackStack("Contact")
                ?.commit()
    }

    private fun initRecyclerView() {
        val adapter = RecentAdapter()

        recent_recyclerview.adapter = adapter
        recent_recyclerview.layoutManager = LinearLayoutManager(context)
        adapter.addHeader(headerView)

        initThreadList()
    }

    private fun initThreadList() {
        Observable.create<List<Recipient>> {
            it.onNext(Repository.getThreadRepo(AMELogin.majorContext)?.getAllThreads()?.map { record ->
                record.getRecipient(AMELogin.majorContext)
            } ?: listOf())
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    threadList.clear()
                    threadList.addAll(it)
                    (recent_recyclerview.adapter as RecentAdapter).setDataList(threadList)
                }, {
                    ALog.e(TAG, "initThreadList error", it)
                })
    }

    private inner class RecentAdapter : LinearBaseAdapter<Recipient>(context) {

        override fun onBindContentHolder(holder: ViewHolder<Recipient>, trueData: Recipient?) {
            if (holder is RecentViewHolder && trueData != null) {
                holder.bind(trueData)
            }
        }

        override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<Recipient> {
            val view = layoutInflater.inflate(R.layout.chats_forward_recent_item, parent, false)
            return RecentViewHolder(view)
        }

    }

    private inner class RecentViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<Recipient>(itemView), RecipientModifiedListener {
        private val portraitView = itemView.findViewById<RecipientAvatarView>(R.id.recent_portrait)
        private val nameView = itemView.findViewById<EmojiTextView>(R.id.recent_name)
        private var recipient: Recipient? = null

        init {
            itemView.setOnClickListener {
                val recipient = this.recipient
                if (recipient != null) {
                    callback?.onClickContact(recipient)
                }
            }
        }

        fun bind(recipient: Recipient) {
            this.recipient?.removeListener(this)
            recipient.addListener(this)
            this.recipient = recipient
            portraitView.showRecipientAvatar(recipient)
            nameView.text = recipient.name
        }

        override fun onModified(recipient: Recipient) {
            if (this.recipient == recipient) {
                bind(recipient)
            }
        }
    }
}