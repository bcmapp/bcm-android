package com.bcm.messenger.chats.mediabrowser.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.*
import com.bcm.messenger.chats.mediabrowser.adapter.FileBrowserAdapter
import com.bcm.messenger.chats.mediabrowser.bean.DeleteFileCallBack
import com.bcm.messenger.chats.mediabrowser.bean.FileBrowserData
import com.bcm.messenger.chats.provider.ChatModuleImp
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.chats_file_browser_fragment.*

/**
 * Created by zjl on 2018/10/16.
 */
class FileBrowserFragment : BaseFragment(), IMediaBrowserMenuProxy, RecipientModifiedListener {

    private val TAG = "FileBrowserFragment"
    private var groupViewModel: GroupMediaBrowserViewModel? = null
    private var privateViewModel: PrivateMediaBrowseModel? = null
    private var commonViewModel: BaseMediaBrowserViewModel? = null
    private var mediaHandleViewModel: MediaHandleViewModel? = null
    private var adapter: FileBrowserAdapter? = null
    private lateinit var recipient: Recipient
    private var mAddress: Address? = null
    private var isDeleteMode: Boolean = false

    private var browserType: Int = BaseMediaBrowserViewModel.TYPE_FILE
    private var threadId: Long = -1L
    private var gid: Long = -1L
    private var beActive: Boolean = false
    private var mediaData: Map<String, List<MediaBrowseData>>? = null
    private var callback: ((selectType: Int) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_file_browser_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.let {act ->
            initView(act)
            browserType = arguments?.getInt(ARouterConstants.PARAM.PARAM_BROWSER_TYPE, BaseMediaBrowserViewModel.TYPE_FILE) ?: BaseMediaBrowserViewModel.TYPE_FILE
            mAddress = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_ADDRESS)

            if (mAddress == null) {
                act.finish()
                return
            }

            mAddress?.let {address ->
                recipient = Recipient.from(accountContext, address.serialize(), true)
                recipient.addListener(this)
                initResource(act)

            }

        }
    }

    override fun onModified(recipient: Recipient) {
        if (this.recipient == recipient) {
            activity?.let {
                initResource(it)
            }
        }
    }


    private fun initResource(activity: FragmentActivity) {

        mediaHandleViewModel = ViewModelProviders.of(activity).get(MediaHandleViewModel::class.java)
        mediaHandleViewModel?.selection?.observe(this, object : Observer<MediaHandleViewModel.SelectionState> {
            var selecting: Boolean = false
            override fun onChanged(it: MediaHandleViewModel.SelectionState?) {
                if (null != it && beActive) {
                    if (selecting != it.selecting) {
                        selecting = it.selecting
                        selectionStateChanged(selecting)
                        adapter?.notifyDataSetChanged()
                    }

                    when {
                        adapter?.getContentSize() == 0 -> callback?.invoke(MediaBrowserActivity.NONE_OBJECT)
                        it.selectionList.size == adapter?.getContentSize() -> callback?.invoke(MediaBrowserActivity.SELECT_ALL)
                        else -> callback?.invoke(MediaBrowserActivity.DESELECT_ALL)
                    }

                }
            }
        })

        mAddress?.let { address ->
            if (address.isGroup) {
                gid = GroupUtil.gidFromAddress(address)
                initGroupResource(activity, gid)
            } else {
                ThreadListViewModel.getThreadId(recipient) {
                    threadId = it
                    initPrivateResource(activity, threadId, getMasterSecret())
                }

            }
        }

    }

    fun initView(context: Context) {

        val layoutManager = LinearLayoutManager(context)
        file_browser_list.layoutManager = layoutManager
        adapter = FileBrowserAdapter(context, accountContext)
        file_browser_list.adapter = adapter

    }


    private fun initGroupResource(activity: FragmentActivity, gid: Long) {
        groupViewModel = ViewModelProviders.of(activity, MediaBrowserModelFactory(accountContext)).get(GroupMediaBrowserViewModel::class.java)
        groupViewModel?.init(gid)
        groupViewModel?.loadMedia(browserType) {
            mediaData = it
            loadMedia(it, groupViewModel ?: return@loadMedia)
        }
    }

    private fun initPrivateResource(activity: FragmentActivity, threadId: Long, masterSecret: MasterSecret) {
        privateViewModel = ViewModelProviders.of(activity, MediaBrowserModelFactory(accountContext)).get(PrivateMediaBrowseModel::class.java)
        privateViewModel?.init(threadId, masterSecret)
        privateViewModel?.loadMedia(browserType) {
            mediaData = it
            loadMedia(it, privateViewModel ?: return@loadMedia)
        }
    }


    private fun loadMedia(map: Map<String, List<MediaBrowseData>>, mediaBrowser: BaseMediaBrowserViewModel) {
        if ((adapter?.itemCount ?: 0) > 0) {
            adapter?.clear()
        }
        commonViewModel = mediaBrowser

        map.let {
            for (key in it.keys) {
                it[key]?.let {
                    val list = ArrayList<FileBrowserData>()
                    it.map {
                        if (!isDeleteMode || it.isDownloaded()) {
                            list.add(getFileBrowserData(it, mediaBrowser))
                        }
                    }
                    if (list.isNotEmpty()) {
                        val title = FileBrowserData(key, it.size, null, mediaBrowser)
                        adapter?.addTitle(title)
                        adapter?.addContent(list)
                    }
                }
            }

            if (adapter?.itemCount == 0) {
                showNoContent()
            } else {
                file_browser_list.visibility = View.VISIBLE
                adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun showNoContent() {
        file_browser_list.visibility = View.GONE
        no_conten_page.visibility = View.VISIBLE
    }


    private fun getFileBrowserData(data: MediaBrowseData, mediaBrowser: BaseMediaBrowserViewModel): FileBrowserData {
        return FileBrowserData(null, 0, data, mediaBrowser, isDeleteMode, object : DeleteFileCallBack {
            override fun delete(list: List<MediaBrowseData>) {
                doForDelete(list.toMutableList()) {

                }
            }

            override fun multiSelect() {
                mediaHandleViewModel?.setSelecting(true)
            }
        })
    }


    fun selectionStateChanged(selecting: Boolean) {

        var fileBrowserData: FileBrowserData?
        for (index in 0 until (adapter?.itemCount ?: 0)) {
            fileBrowserData = adapter?.getFileBrowserData(index)
            fileBrowserData?.isInSelecting = selecting
        }
    }

    override fun forward() {
        val list = mediaHandleViewModel?.selection?.value?.selectionList
        if (list?.isNotEmpty() == true) {
            val fileData = list[0]
            ChatModuleImp().forwardMessage(AppContextHolder.APP_CONTEXT, fileData.fromGroup, if (fileData.fromGroup) gid else threadId, list.map { it.msgSource }.toSet()) {
                if (it.isEmpty()) {
                    mediaHandleViewModel?.clearSelectionList()
                    mediaHandleViewModel?.setSelecting(false)
                }
            }

        } else {
            ToastUtil.show(AppContextHolder.APP_CONTEXT, AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_select_file_first))
        }
    }

    override fun save() {
        mediaHandleViewModel?.selection?.value?.let {
            AmePopup.loading.show(activity, false)
            commonViewModel?.download(it.selectionList.toList()) { fail ->
                AmePopup.loading.dismiss()
                if (fail.isNotEmpty()) {
                    val content = StringBuilder()
                    for ((index, f) in fail.withIndex()) {
                        if (index < fail.size - 1) {
                            content.append(f.name + ",")
                        } else {
                            content.append(f.name)
                        }
                    }
                    content.append(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_save_fail))
                    AmeAppLifecycle.failure(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_save_fail), true)
                } else {
                    AmeAppLifecycle.succeed(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_save_success), true)
                }
                adapter?.notifyDataSetChanged()
                mediaHandleViewModel?.clearSelectionList()
                mediaHandleViewModel?.setSelecting(false)
            }
        }
    }

    override fun delete() {
        mediaHandleViewModel?.selection?.value?.let {
            if (it.selectionList.size > 0) {
                doForDelete(mediaHandleViewModel?.selection?.value?.selectionList
                        ?: mutableListOf()) {
                    mediaHandleViewModel?.clearSelectionList()
                    mediaHandleViewModel?.setSelecting(false)
                }

            } else {
                ToastUtil.show(AppContextHolder.APP_CONTEXT, AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_select_file_first))
            }
        }
    }

    private fun doForDelete(selectionList: MutableList<MediaBrowseData>, callback: (fail: List<MediaBrowseData>) -> Unit) {
        AmePopup.bottom.newBuilder()
                .withTitle(getString(R.string.chats_media_browser_delete_fromat, selectionList.size))
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_media_browser_delete), getColorCompat(R.color.common_color_ff3737)) {
                    deleteSelectedItem(selectionList)
                    callback(emptyList())
                })
                .withDoneTitle(getString(R.string.common_cancel))
                .show(activity)
    }

    private fun deleteSelectedItem(selectionList: MutableList<MediaBrowseData>) {
        AmeAppLifecycle.showLoading()
        commonViewModel?.delete(selectionList) { fail ->
            AmeAppLifecycle.hideLoading()
            if (fail.isNotEmpty()) {
                val content = StringBuilder()
                for ((index, f) in fail.withIndex()) {
                    if (index < fail.size - 1) {
                        content.append(f.name + ",")
                    } else {
                        content.append(f.name)
                    }
                }
                content.append(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_delete_fail))
                AmeAppLifecycle.failure(content.toString(), true)

                selectionList.removeAll(fail)
            } else {
                AmeAppLifecycle.succeed(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_delete_success), true)
            }

            mediaData?.let {
                for (key in it.keys) {
                    if (it[key] != null) {
                        (it[key] as MutableList<MediaBrowseData>).removeAll(selectionList)
                        if (it[key]?.isEmpty() == true) {
                            (it as MutableMap).remove(key)
                        }
                    }
                }
            }

            loadMedia(mediaData ?: return@delete, commonViewModel ?: return@delete)
        }
    }

    override fun active(beActive: Boolean) {
        if (this.beActive != beActive) {
            this.beActive = beActive
            adapter?.notifyDataSetChanged()
        }
    }

    fun setDeleteMode(deleteMode: Boolean) {
        this.isDeleteMode = deleteMode
    }

    private fun selectAll(list: MutableList<FileBrowserData>) {
        if (list.size > 0) {
            val mediaList: MutableList<MediaBrowseData> = mutableListOf()
            var size = 0L
            list.forEach {
                it.data?.let {
                    size += it.fileSize()
                    mediaList.add(it)
                }
            }
            mediaHandleViewModel?.selectAll(mediaList, size)
        }
    }

    private fun cancelAll() {
        mediaHandleViewModel?.cancelSelectAll()
    }

    fun dillSelect(): Int {
        return if ((adapter?.itemCount ?: 0) > 0) {
            var browserData: FileBrowserData
            val list = mutableListOf<FileBrowserData>()
            for (index in 0 until (adapter?.itemCount ?: 0)) {
                browserData = adapter?.getFileBrowserData(index) ?: continue
                if (browserData.title == null) {
                    list.add(browserData)
                }
            }
            if (list.size == mediaHandleViewModel?.selection?.value?.selectionList?.size) {
                ALog.d(TAG, "cancel All")
                cancelAll()
                MediaBrowserActivity.DESELECT_ALL
            } else {
                ALog.d(TAG, "selectAll All")
                selectAll(list)
                MediaBrowserActivity.SELECT_ALL
            }
        } else {
            ALog.d(TAG, "none object")
            MediaBrowserActivity.NONE_OBJECT
        }
    }

    fun selectCallBack(callback: (selectType: Int) -> Unit) {
        this.callback = callback
    }
}