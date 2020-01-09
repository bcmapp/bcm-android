package com.bcm.messenger.me.ui.block

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.provider.accountmodule.IContactModule
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.me.R
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_activity_block_users.*
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by cangwang
 */
class BlockUsersActivity : AccountSwipeBaseActivity() {

    private val TAG = "BlockUserActivity"
    private var isEdit = false
    private var adapter: BlockUserAdapter? = null

    private val selectList: MutableList<Recipient> = ArrayList()
    private var contactProvider: IContactModule? = null

    private var mRightMenu: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_block_users)

        block_user_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                if (isEdit) selectBack()
                else finish()
            }

            override fun onClickRight() {
                if (isEdit) done()
                else edit()
            }
        })

        mRightMenu = block_user_title_bar.getRightView().second as? TextView

        contactProvider = AmeModuleCenter.contact(accountContext)

        initAdapter()
    }

    override fun onBackPressed() {
        if (isEdit) selectBack()
        else super.onBackPressed()
    }

    @SuppressLint("CheckResult")
    private fun initAdapter() {
        adapter = BlockUserAdapter { if (it == 0) no_block_users.visibility = View.VISIBLE }
        block_user_list.adapter = adapter
        block_user_list.layoutManager = LinearLayoutManager(this)
        Observable.create(ObservableOnSubscribe<MutableList<Recipient>> {
            val recipientList = ArrayList<Recipient>()
            val blockUsers = Repository.getRecipientRepo(accountContext)?.getBlockedUsers()
            blockUsers?.forEach { user ->
                recipientList.add(Recipient.fromSnapshot(accountContext, user.uid, user))
            }
            it.onNext(recipientList)
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.size > 0) {
                        no_block_users.visibility = View.GONE
                        adapter?.addList(it)
                    } else {
                        no_block_users.visibility = View.VISIBLE
                    }
                }, {
                    ALog.e(TAG, "find block receipient fail", it)
                })
    }

    fun selectBack() {
        isEdit = false
        adapter?.resume()
        mRightMenu?.text = getString(R.string.me_self_edit_button)
    }

    fun edit() {
        isEdit = true
        mRightMenu?.text = getString(R.string.common_done)
        adapter?.removeVisible(true)
    }

    fun done() {
        isEdit = false
        mRightMenu?.text = getString(R.string.me_self_edit_button)
        adapter?.removeVisible(false)
        if (selectList.size > 0) {
            adapter?.removeList(selectList)
        }
    }

    inner class BlockUserAdapter(result: (size: Int) -> Unit) : RecyclerView.Adapter<BlockUserViewHolder>() {
        private var blockUserList: MutableList<Recipient> = CopyOnWriteArrayList()
        private var isRemoveVisible = false
        private var recordBlockList: MutableList<Recipient> = CopyOnWriteArrayList()
        private var callback = result

        fun addList(list: MutableList<Recipient>) {
            if (list.size > 0) {
                blockUserList.addAll(list)
                notifyDataSetChanged()
            }
        }

        fun resume() {
            if (isRemoveVisible) {
                isRemoveVisible = false
                blockUserList.clear()
                blockUserList.addAll(recordBlockList)
                notifyDataSetChanged()
            }
        }

        fun removeVisible(visible: Boolean) {
            isRemoveVisible = visible
            if (visible) {
                recordBlockList.clear()
                recordBlockList.addAll(blockUserList)
            }
            notifyDataSetChanged()
        }

        fun removeShow(recipient: Recipient) {
            blockUserList.remove(recipient)
            notifyDataSetChanged()
        }

        @SuppressLint("CheckResult")
        fun remove(recipient: Recipient) {
            contactProvider?.blockContact(recipient.address, false) {
                if (it) {
                    blockUserList.remove(recipient)
                    callback.invoke(blockUserList.size)
                    notifyDataSetChanged()
                }
            }
        }

        @SuppressLint("CheckResult")
        fun removeList(list: MutableList<Recipient>) {
            if (list.size > 0) {
                recordBlockList.removeAll(list)

                val addressList = list.map { it.address }
                contactProvider?.blockContact(addressList, block = false) { resultList ->
                    for (address in resultList) {
                        val r = list.find { it.address == address }
                        if (r != null) {
                            recordBlockList.remove(r)
                        }
                    }
                    blockUserList.removeAll(recordBlockList)
                    callback.invoke(blockUserList.size)
                    selectList.clear()
                    notifyDataSetChanged()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockUserViewHolder {
            return BlockUserViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.me_item_block_user, parent, false))
        }

        override fun getItemCount(): Int {
            return blockUserList.size
        }

        override fun onBindViewHolder(holder: BlockUserViewHolder, position: Int) {
            val recipient = blockUserList[position]
            holder.bindData(recipient, isRemoveVisible)
            holder.selectItem.setOnClickListener {
                selectList.add(recipient)
                removeShow(recipient)
            }

            holder.itemView.setOnLongClickListener {
                if (!isEdit) {
                    edit()
                    return@setOnLongClickListener true
                }
                return@setOnLongClickListener false
            }
        }
    }

    inner class BlockUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val selectItem = itemView.findViewById<ImageView>(R.id.block_user_select)!!
        val recipientImg = itemView.findViewById<IndividualAvatarView>(R.id.block_user_img)!!
        val recipientName = itemView.findViewById<TextView>(R.id.block_user_name)!!

        fun bindData(recipient: Recipient, isRemoveVisible: Boolean) {
            if (isRemoveVisible) {
                selectItem.visibility = View.VISIBLE
            } else {
                selectItem.visibility = View.GONE
            }
            recipientImg.setPhoto(recipient)
            recipientName.text = recipient.name
        }
    }
}