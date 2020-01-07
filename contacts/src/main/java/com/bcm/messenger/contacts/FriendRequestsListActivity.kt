package com.bcm.messenger.contacts

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriendRequest
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.contacts_activity_friend_request.*
import kotlinx.android.synthetic.main.contacts_item_friend_request.view.*

/**
 * Created by Kin on 2019/5/17
 */
@Route(routePath = ARouterConstants.Activity.FRIEND_REQUEST_LIST)
class FriendRequestsListActivity : SwipeBaseActivity() {
    private val TAG = "FriendRequestsListActivity"

    private lateinit var viewModel: FriendRequestsListViewModel
    private lateinit var adapter: RequestAdapter

    private var requestList = listOf<BcmFriendRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.contacts_activity_friend_request)

        viewModel = ViewModelProviders.of(this).get(FriendRequestsListViewModel::class.java).apply {
            setAccountContext(accountContext)
        }

        initView()
        initData()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        viewModel.queryData()
    }

    private fun initView() {
        friend_req_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        adapter = RequestAdapter()
        friend_req_list.adapter = adapter
        friend_req_list.layoutManager = LinearLayoutManager(this)
    }

    private fun initData() {

        viewModel.listLiveData.observe(this, Observer {
            ALog.i(TAG, "Get request list, size = ${it?.size}")
            if (it != null) {
                requestList = it
                adapter.notifyDataSetChanged()
                friend_req_empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }
        })
        viewModel.queryData()
    }

    private inner class RequestAdapter : RecyclerView.Adapter<RequestViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
            val view = layoutInflater.inflate(R.layout.contacts_item_friend_request, parent, false)
            return RequestViewHolder(view)
        }

        override fun getItemCount(): Int {
            return requestList.size
        }

        override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
            holder.bind(requestList[position])
        }

        override fun onViewRecycled(holder: RequestViewHolder) {
            holder.unbind()
        }
    }

    private inner class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), RecipientModifiedListener {
        private var recipient: Recipient? = null

        fun bind(data: BcmFriendRequest) {
            recipient?.removeListener(this)
            recipient = Recipient.from(accountContext, Address.fromSerialized(data.proposer), true)
            recipient?.addListener(this)

            itemView.friend_req_avatar.setPhoto(recipient)
            itemView.friend_req_name.text = recipient?.name

            if (data.memo.isNotEmpty()) {
                itemView.friend_req_detail.visibility = View.VISIBLE
                itemView.friend_req_detail.text = data.memo
            } else {
                itemView.friend_req_detail.visibility = View.GONE
            }

            itemView.friend_req_new.visibility = if (data.isUnread()) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                data.setRead()
                itemView.friend_req_new.visibility = View.GONE
                startBcmActivity(Intent(this@FriendRequestsListActivity, FriendRequestHandleActivity::class.java).apply {
                    putExtra("id", data.id)
                })
            }
        }

        fun unbind() {
            recipient?.removeListener(this)
        }

        override fun onModified(recipient: Recipient) {
            if (this.recipient == recipient) {
                itemView.friend_req_avatar.setPhoto(recipient)
                itemView.friend_req_name.text = recipient.name
            }
        }
    }
}