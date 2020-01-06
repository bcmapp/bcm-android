package com.bcm.messenger.chats.mediabrowser.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.*
import com.bcm.messenger.chats.mediabrowser.adapter.MediaBrowserAdapter
import com.bcm.messenger.chats.mediapreview.MediaViewActivity
import com.bcm.messenger.chats.provider.ChatModuleImp
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.AppContextHolder
import kotlinx.android.synthetic.main.chats_media_image_browser.*
import java.lang.ref.WeakReference

/**
 * bcm.social.01 2018/10/16.
 */
class MediaBrowserFragment : BaseFragment(), IMediaBrowserMenuProxy {

    private val TAG = "MediaBrowserFragment"

    private var beActive = false
    private var mHandleViewModel: MediaHandleViewModel? = null
    private var browserAdapter: MediaBrowserAdapter? = null
    private var viewModel: BaseMediaBrowserViewModel? = null

    private var gid: Long = -1L
    private var threadId: Long = -1L
    private var indexId: Long = -1L
    private var isDeleteMode = false

    private var callback: ((selectType: Int) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_media_image_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val address = arguments?.getParcelable<Address>(ARouterConstants.PARAM.PARAM_ADDRESS)
        indexId = arguments?.getLong(ARouterConstants.PARAM.PARAM_INDEX_ID) ?: -1L
        val act = activity
        if (null == address || null == act){
            return
        }
        val handleViewModel = ViewModelProviders.of(act).get(MediaHandleViewModel::class.java)
        mHandleViewModel = handleViewModel
        if (!address.isGroup) {
            val model = ViewModelProviders.of(act).get(PrivateMediaBrowseModel::class.java)
            val masterSecret = BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT)
            val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, address, true)
            ThreadListViewModel.getThreadId(recipient) {
                threadId = it
                if (null != masterSecret && threadId >= 0){
                    model.init(threadId, masterSecret)
                    init(view.context, address, model, handleViewModel)
                }
            }
        } else {
            val model = ViewModelProviders.of(act).get(GroupMediaBrowserViewModel::class.java)
            gid = GroupUtil.gidFromAddress(address)
            model.init(gid)
            init(view.context, address, model, handleViewModel)
        }

    }

    private fun init(context: Context, address: Address, viewModel: BaseMediaBrowserViewModel, handleViewModel: MediaHandleViewModel) {
        //
        media_browser_recycler_view.layoutManager = GridLayoutManager(context, 3)
        val browserAdapter = MediaBrowserAdapter(viewModel, handleViewModel, no_conten_page, isDeleteMode, context) { dataList ->
            indexId.let {
                for ((index, data) in dataList.withIndex()) {
                    if (data.msgSource is MessageRecord) {
                        if (data.msgSource.id == indexId) {
                            media_browser_recycler_view.smoothScrollToPosition(index)
                        }
                    } else if (data.msgSource is AmeGroupMessageDetail) {
                        if (data.msgSource.indexId == indexId) {
                            media_browser_recycler_view.smoothScrollToPosition(index)
                        }
                    }
                }
            }
        }
        browserAdapter.beActive = beActive
        media_browser_recycler_view.adapter = browserAdapter
        browserAdapter.onDataClicked = {
            v, data ->
            if (!address.isGroup){
                showPrivateMediaView(v, data)
            } else {
                showGroupMediaView(v, data)
            }
        }
        this.browserAdapter = browserAdapter
        this.viewModel = viewModel

        handleViewModel.selection.observe(this, object : Observer<MediaHandleViewModel.SelectionState> {

            override fun onChanged(it: MediaHandleViewModel.SelectionState?) {

                if (null != it && beActive) {
                    browserAdapter.isInSelecting = it.selecting
                    when {
                        browserAdapter.itemCount == 0 -> callback?.invoke(MediaBrowserActivity.NONE_OBJECT)
                        it.selectionList.size == browserAdapter.itemCount -> callback?.invoke(MediaBrowserActivity.SELECT_ALL)
                        else -> callback?.invoke(MediaBrowserActivity.DESELECT_ALL)
                    }
                }
            }
        })

        viewModel.mediaListLiveData.observe(this, Observer {
            it?.let { map ->
                browserAdapter.updateAdapterData(map)
            }
        })
    }

    private fun showPrivateMediaView(mediaView: View, data: MediaBrowseData) {
        val intent = Intent(context, MediaViewActivity::class.java)
        val msg = data.msgSource as? MessageRecord
        val attachment = msg?.getMediaAttachment()
        val act = activity
        if (null != attachment && null != act && !act.isFinishing){
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(MediaViewActivity.THREAD_ID, msg.threadId)
            intent.putExtra(MediaViewActivity.INDEX_ID, msg.id)
            intent.putExtra(MediaViewActivity.DATA_TYPE, MediaViewActivity.TYPE_PRIVATE_BROWSER)
            if (MediaUtil.isGif(data.mediaType)) {  //
                act.startActivity(intent)
            } else {
                act.startActivity(intent, ActivityOptionsCompat.makeSceneTransitionAnimation(act, mediaView, ShareElements.Activity.MEDIA_PREIVEW + msg.id).toBundle())
            }
        }
    }

    private fun showGroupMediaView(mediaView: View, data:MediaBrowseData){
        val intent = Intent(context, MediaViewActivity::class.java)
        val msg = data.msgSource as? AmeGroupMessageDetail
        val act = activity
        if (null != msg && act != null && !act.isFinishing) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(MediaViewActivity.THREAD_ID, msg.gid)
            intent.putExtra(MediaViewActivity.INDEX_ID, msg.indexId)
            intent.putExtra(MediaViewActivity.DATA_TYPE, MediaViewActivity.TYPE_GROUP_BROWSER)
            if (MediaUtil.isGif(data.mediaType)) {  //
                act.startActivity(intent)
            } else {
                act.startActivity(intent, ActivityOptionsCompat.makeSceneTransitionAnimation(act, mediaView, ShareElements.Activity.MEDIA_PREIVEW + msg.indexId).toBundle())
            }
        }
    }

    override fun forward() {
        val list = mHandleViewModel?.selection?.value?.selectionList
        if (list?.isNotEmpty() == true) {
            val fileData = list[0]
            ChatModuleImp().forwardMessage(activity ?: return, fileData.fromGroup,
                    if(fileData.fromGroup) gid else threadId, list.map { it.msgSource }.toSet()) {
                if(it.isEmpty()) {
                    mHandleViewModel?.clearSelectionList()
                    mHandleViewModel?.setSelecting(false)
                }
            }

        }
    }

    override fun save() {
        AmeAppLifecycle.showLoading()
        val weakThis = WeakReference(this)
        mHandleViewModel?.selection?.value?.let {
            viewModel?.download(it.selectionList.toList()) { fail ->
                AmeAppLifecycle.hideLoading()
                if (fail.isNotEmpty()) {
                    AmeAppLifecycle.failure(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_save_fail), true)
                } else {
                    weakThis.get()?.mHandleViewModel?.setSelecting(false)
                    AmeAppLifecycle.succeed(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_save_success), true)
                }
            }
        }
    }

    override fun delete() {
        val weakThis = WeakReference(this)
        AmePopup.bottom.newBuilder()
                .withTitle(getString(R.string.chats_media_browser_delete_fromat, (mHandleViewModel?.selection?.value?.selectionList?.size ?: 0)))
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_media_browser_delete), getColorCompat(R.color.common_color_ff3737)) {
                    AmeAppLifecycle.showLoading()
                    mHandleViewModel?.selection?.value?.let {
                        val list = it.selectionList.toList()
                        viewModel?.delete(list) { fail ->
                            AmeAppLifecycle.hideLoading()
                            if (fail.isNotEmpty()) {
                                AmeAppLifecycle.failure(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_delete_fail), true)
                            } else {
                                val safeThis = weakThis.get()
                                if (null != safeThis && !safeThis.isDetached){
                                    weakThis.get()?.browserAdapter?.delete(list)
                                    weakThis.get()?.mHandleViewModel?.setSelecting(false)
                                }
                                AmeAppLifecycle.succeed(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_delete_success), true)
                            }
                        }
                    }
                })
                .withDoneTitle(getString(R.string.common_cancel))
                .show(activity)
    }

    override fun active(beActive: Boolean) {
        if (this.beActive != beActive) {
            this.beActive = beActive
            browserAdapter?.beActive = beActive
        }
    }

    fun selectCallBack(callback: (selectType: Int) -> Unit) {
        this.callback = callback
    }

    fun setDeleteMode(deleteMode: Boolean) {
        this.isDeleteMode = deleteMode
    }

    private fun selectAll(list: MutableList<MediaBrowseData>) {
        if (list.isNotEmpty()) {
            var size = 0L
            list.forEach {
                size += it.fileSize()
                it.selected = true
            }
            mHandleViewModel?.selectAll(list, size)
            browserAdapter?.notifyDataSetChanged()
        }
    }

    private fun cancelAll(list: MutableList<MediaBrowseData>) {
        if (list.isNotEmpty()) {
            list.forEach {
                it.selected = false
            }
            mHandleViewModel?.cancelSelectAll()
            browserAdapter?.notifyDataSetChanged()
        }
    }

    fun dillSelect(): Int {
        val dataList = browserAdapter?.getDataList()
        dataList?.let {
            return if (it.size == mHandleViewModel?.selection?.value?.selectionList?.size) {
                cancelAll(it)
                MediaBrowserActivity.DESELECT_ALL
            } else {
                selectAll(it)
                MediaBrowserActivity.SELECT_ALL
            }
        }
        return MediaBrowserActivity.NONE_OBJECT
    }

}