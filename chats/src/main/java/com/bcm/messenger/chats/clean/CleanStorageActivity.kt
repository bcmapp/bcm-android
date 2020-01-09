package com.bcm.messenger.chats.clean

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.ui.MediaBrowserActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.bean.ConversationStorage
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.RecipientAvatarView
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.adapter.ListDataSource
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.chats_activity_clean_storage.*

/**
 * bcm.social.01 2018/11/21.
 */
@Route(routePath = ARouterConstants.Activity.CLEAN_STORAGE)
class CleanStorageActivity : AccountSwipeBaseActivity(), AmeRecycleViewAdapter.IViewHolderDelegate<Address> {

    private val TAG = "CleanStorageActivity"
    private var allConversationStorage = ConversationStorage(0, 0, 0)
    private var cleanStoragePopCreator: CleanAllConversationStorageSelectionViewCreater? = null

    private val dataSource = ListDataSource<Address>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_clean_storage)

        conversation_list_view.layoutManager = LinearLayoutManager(this)
        val adapter = AmeRecycleViewAdapter(this, dataSource)
        adapter.setHasStableIds(true)
        conversation_list_view.adapter = adapter
        adapter.setViewHolderDelegate(this)

        CleanConversationStorageLogic.addCallback(TAG, object : CleanConversationStorageLogic.ConversationStorageCallback {

            override fun onCollect(finishedConversation: Address?, allFinished: Boolean) {
                ALog.i(TAG, "onCollect finished: $finishedConversation, allFinished: $allFinished")
                updateConversationList(finishedConversation, allFinished)
            }

            override fun onClean(finishedConversation: Address?, allFinished: Boolean) {
                ALog.i(TAG, "onClean finished: $finishedConversation, allFinished: $allFinished")
                updateConversationList(finishedConversation, allFinished)
                if (finishedConversation == null && allFinished) {
                    if (!isFinishing) {
                        AmePopup.loading.dismiss()
                        AmePopup.result.succeed(this@CleanStorageActivity, getString(R.string.chats_clean_succeed))
                        allConversationStorage = CleanConversationStorageLogic.getAllConversationStorageSize()
                        dataSource.refresh()
                    }
                }
            }
        })

        data_storage_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        CleanConversationStorageLogic.collectionAllConversationStorageSize(accountContext)
    }

    override fun onResume() {
        super.onResume()
        if (CleanConversationStorageLogic.isAllCollectionFinished()) {
            allConversationStorage = CleanConversationStorageLogic.getAllConversationStorageSize()
        }
        dataSource.refresh()
    }

    override fun finish() {
        super.finish()
        CleanConversationStorageLogic.cancelCollectionAllConversationStorageSize()
        CleanConversationStorageLogic.removeCallback(TAG)
    }


    private fun updateConversationList(finishedConversation: Address?, allFinished: Boolean) {
        ALog.i(TAG, "updateConversationList finishedAddress: $finishedConversation, allFinished: $allFinished")
        if (!isFinishing) {
            if (allFinished) {
                allConversationStorage = CleanConversationStorageLogic.getAllConversationStorageSize()
            }
            if (dataSource.size() == 0) {
                dataSource.updateDataSource(CleanConversationStorageLogic.getConversationList())
            } else {
                dataSource.refresh()
            }
        }
    }

    private fun showCleanAllConversationStoragePop() {
        val cleanStoragePopCreator = CleanAllConversationStorageSelectionViewCreater(allConversationStorage) {
            if (CleanConversationStorageLogic.getStorageSizeByType(it) == 0L) {
                AmePopup.bottom.dismiss()
                return@CleanAllConversationStorageSelectionViewCreater
            }
            val type = it
            AmePopup.bottom.newBuilder()
                    .withTitle(getString(R.string.chats_clear_selection_storage))
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_clean_clear), AmeBottomPopup.PopupItem.CLR_RED) {
                        clearAll(type)
                    }).withDoneTitle(getString(R.string.common_cancel)).show(this)
        }

        this.cleanStoragePopCreator = cleanStoragePopCreator

        AmePopup.bottom.newBuilder()
                .withCustomView(cleanStoragePopCreator)
                .withDoneTitle(getString(R.string.common_cancel))
                .withDismissListener {
                    this.cleanStoragePopCreator = null
                }
                .show(this)
    }

    private fun clearAll(type: Int) {
        AmePopup.loading.show(this@CleanStorageActivity)
        CleanConversationStorageLogic.clearAllConversationMediaMessage(accountContext, type)
    }


    override fun getViewHolderType(adapter: AmeRecycleViewAdapter<Address>, position: Int, data: Address): Int {
        return when (data) {
            CleanConversationStorageLogic.ADDRESS_ALL -> R.layout.chats_data_storage_title_cell
            else -> R.layout.chats_data_storage_item_cell
        }
    }

    override fun bindViewHolder(adapter: AmeRecycleViewAdapter<Address>, viewHolder: AmeRecycleViewAdapter.ViewHolder<Address>) {
        when (viewHolder) {
            is AllStorageHolder -> {
                if (CleanConversationStorageLogic.isAllCollectionFinished()) {
                    viewHolder.sizeView.text = StringAppearanceUtil.formatByteSizeString(allConversationStorage.storageUsed())
                } else {
                    viewHolder.sizeView.text = getString(R.string.chats_clean_calculating)
                }
            }
            is ConversationStorageHolder -> {
                val data = viewHolder.getData()
                if (null != data) {
                    if (CleanConversationStorageLogic.isCollectedFinished(data)) {
                        val size = CleanConversationStorageLogic.getConversationStorageSize(data)
                        viewHolder.sizeView.text = StringAppearanceUtil.formatByteSizeString(size)
                    } else {
                        viewHolder.sizeView.text = getString(R.string.chats_clean_calculating)
                    }
                    val recipient = Recipient.from(data, true)
                    viewHolder.recipientPhotoView.showRecipientAvatar(recipient)
                    viewHolder.nameView.text = recipient.name
                }
            }
        }
    }

    override fun unbindViewHolder(adapter: AmeRecycleViewAdapter<Address>, viewHolder: AmeRecycleViewAdapter.ViewHolder<Address>) {
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<Address>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<Address> {
        return when (viewType) {
            R.layout.chats_data_storage_title_cell -> {
                AllStorageHolder(inflater.inflate(viewType, parent, false), this)
            }
            else -> {
                ConversationStorageHolder(inflater.inflate(viewType, parent, false))
            }
        }
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<Address>, viewHolder: AmeRecycleViewAdapter.ViewHolder<Address>) {
        val itemData = viewHolder.getData()
        if (null != itemData && itemData != CleanConversationStorageLogic.ADDRESS_ALL) {
            if (CleanConversationStorageLogic.isCollectedFinished(itemData)) {
                MediaBrowserActivity.router(accountContext, itemData, true)
            } else {
                ToastUtil.show(this@CleanStorageActivity, getString(R.string.chats_collecting_wait_text))
            }
        }
    }


    class AllStorageHolder(view: View, activity: CleanStorageActivity) : AmeRecycleViewAdapter.ViewHolder<Address>(view) {
        val sizeView = view.findViewById<TextView>(R.id.clear_conversation_size)

        init {
            view.findViewById<TextView>(R.id.text_clear_all).setOnClickListener {
                if (CleanConversationStorageLogic.isAllCollectionFinished()) {
                    activity.showCleanAllConversationStoragePop()
                } else {
                    ToastUtil.show(it.context, "Statistics in progress, please try again later ...")
                }
            }
        }
    }

    class ConversationStorageHolder(view: View) : AmeRecycleViewAdapter.ViewHolder<Address>(view) {
        val sizeView = view.findViewById<TextView>(R.id.clear_conversation_size)
        val nameView = view.findViewById<TextView>(R.id.clear_conversation_name)
        val recipientPhotoView = view.findViewById<RecipientAvatarView>(R.id.clear_conversation_photo)
    }

}