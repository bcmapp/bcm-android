package com.bcm.messenger.chats.mediapreview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.SharedElementCallback
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.forward.ForwardActivity
import com.bcm.messenger.chats.mediabrowser.ui.MediaBrowserActivity
import com.bcm.messenger.chats.mediapreview.bean.*
import com.bcm.messenger.chats.mediapreview.viewmodel.MediaViewGroupViewModel2
import com.bcm.messenger.chats.mediapreview.viewmodel.MediaViewHistoryViewModel
import com.bcm.messenger.chats.mediapreview.viewmodel.MediaViewPrivateViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.chats_activity_media_view.*

/**
 * 
 * Created by Kin on 2018/10/31
 */
class MediaViewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MediaViewActivity"

        const val TYPE_PRIVATE = 0    // Private chat
        const val TYPE_GROUP = 1      // Group chat
        const val TYPE_HISTORY = 2    // History message
        const val TYPE_SINGLE = 3     // Just show one image
        const val TYPE_PRIVATE_BROWSER = 4
        const val TYPE_GROUP_BROWSER = 5

        const val DATA_TYPE = "__data_type"          // Data type, use type enum above
        const val THREAD_ID = "__thread_id"          // Private chat thread id or group chat gid
        const val INDEX_ID = "__index_id"            // Message database index id
        const val HISTORY_INDEX = "__history_index"  // History message clicked index
        const val MEDIA_URI = "__media_uri"          // Uri of single image

        fun isContentTypeSupported(contentType: String?): Boolean {
            return MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType)
        }
    }

    private lateinit var viewModel: BaseMediaViewModel
    private lateinit var adapter: MediaViewPagerAdapter
    private lateinit var masterSecret: MasterSecret
    private val mediaDataList = mutableListOf<MediaViewData>()
    private var enterTransitionId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_media_view)

        val ms = BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT)
        if (ms == null) {
            ALog.i(TAG, "Get master secret failed, activity finish")
            finish()
            return
        }
        masterSecret = ms

        enterFullScreen()
        initView()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(setLocale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        mediaDataList.clear()
        adapter.notifyDataSetChanged()

        initResources()
    }

    private fun enterFullScreen() {
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun exitFullScreen() {
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    private fun initView() {
        window.decorView.setBackgroundColor(Color.BLACK)

        adapter = MediaViewPagerAdapter()
        chats_media_view_pager.adapter = adapter

        initOnClickListeners()

        postponeEnterTransition()

        initResources()
    }

    private fun initOnClickListeners() {
        chats_media_more_view.setMoreViewListener(object : MediaMoreView.MoreViewActionListener {
            override fun clickDownload() {
                saveMedia()
            }

            // 跳转到多媒体列表
            override fun clickMediaBrowser() {
                val f = (adapter.getCurrentFragment() as? MediaViewFragment) ?: return
                val data = f.getData()
                if (data == null || data.msgType == MSG_TYPE_HISTORY) {
                    return
                }
                val intent = Intent(this@MediaViewActivity, MediaBrowserActivity::class.java).apply {
                    val address: Address = if (data.msgType == MSG_TYPE_PRIVATE) {
                        (data.sourceMsg as MessageRecord).getRecipient().address
                    } else {
                        GroupUtil.addressFromGid((data.sourceMsg as AmeGroupMessageDetail).gid)
                    }
                    putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, address)
                    putExtra(ARouterConstants.PARAM.PARAM_INDEX_ID, data.indexId)
                }
                startActivity(intent)
            }

            override fun moreOptionVisibilityChanged(isShow: Boolean) {
            }
        })
    }

    private val fragmentListener = object : MediaViewFragment.MediaViewFragmentActionListener {
        override fun videoPlaying() {
        }

        override fun clickImage() {
            dismissActivity()
        }

        override fun longClickImage(): Boolean {
            showSheet()
            return true
        }

        override fun controllerVisible(isVisible: Boolean) {
            if (isVisible) {
                chats_media_more_view.hideDefaultOptionLayout()
            } else {
                chats_media_more_view.showDefaultOptionLayout()
            }
        }

        override fun dataIsVideo(isVideo: Boolean) {
            if (isVideo) {
                chats_media_more_view.displayNull()
            } else {
                chats_media_more_view.showDefaultOptionLayout()
                chats_media_more_view.displayDefault()
            }
        }

        override fun dataIsValid(isValid: Boolean) {
            if (isValid) {
                chats_media_more_view.enableDownload()
            } else {
                chats_media_more_view.disableDownload()
            }
        }
    }

    private fun showSheet() {
        val popup = AmePopup.bottom.newBuilder()
                .withDoneTitle(getString(R.string.chats_cancel))

        val data = (adapter.getCurrentFragment() as? MediaViewFragment)?.getData()
        if (data != null && (data.msgType == MSG_TYPE_PRIVATE || (data.sourceMsg as? AmeGroupMessageDetail)?.isAttachmentComplete == true)) {
            popup.withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_forward)) {
                forwardMedia()
            })
        }
        popup.withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_save)) {
            saveMedia()
        }).withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_delete)) {
            deleteMedia()
        })

        popup.show(this)
    }

    private fun forwardMedia() {
        val f = (adapter.getCurrentFragment() as? MediaViewFragment) ?: return
        val data = f.getData()
        if (data == null || data.msgType == MSG_TYPE_HISTORY) {
            return
        }
        val intent = Intent(this@MediaViewActivity, ForwardActivity::class.java).apply {
            val gid = if (data.msgType == MSG_TYPE_PRIVATE) {
                ARouterConstants.PRIVATE_MEDIA_CHAT
            } else {
                (data.sourceMsg as AmeGroupMessageDetail).gid
            }
            putExtra(ForwardActivity.GID, gid)
            putExtra(ForwardActivity.INDEX_ID, data.indexId)
        }
        startActivity(intent)
    }

    private fun saveMedia() {
        val f = (adapter.getCurrentFragment() as? MediaViewFragment) ?: return
        val data = f.getData()
        chats_media_more_view.displaySpinning()
        viewModel.saveData(data) { success ->
            chats_media_more_view.displayDefault()
            if (success) {
                AmeAppLifecycle.succeed(getString(R.string.chats_save_success), true)
            } else {
                AmeAppLifecycle.failure(getString(R.string.chats_save_fail), true)
            }
        }
    }

    private fun deleteMedia() {
        val f = (adapter.getCurrentFragment() as? MediaViewFragment) ?: return
        val data = f.getData()
        if (data == null || data.msgType == MSG_TYPE_HISTORY) {
            return
        }
        AmeAppLifecycle.show(
                getString(R.string.chats_media_browser_delete_title),
                getString(R.string.chats_media_browser_delete),
                getString(R.string.chats_cancel)) {
            chats_media_more_view.displaySpinning()
            viewModel.deleteData(data) { success ->
                chats_media_more_view.displayDefault()
                if (success) {
                    AmeAppLifecycle.succeed(getString(R.string.chats_delete_success), true) {
                        if (mediaDataList.size == 1) {
                            finish()
                        } else {
                            mediaDataList.remove(data)
                            adapter.notifyDataSetChanged()
                        }
                    }
                } else {
                    AmeAppLifecycle.failure(getString(R.string.chats_delete_fail), true)
                }
            }
        }
    }

    private fun initResources() {
        val type = intent.getIntExtra(DATA_TYPE, TYPE_GROUP)
        if (type == TYPE_SINGLE) {
            initSingleResources()
            return
        }

        val threadId = intent.getLongExtra(THREAD_ID, -1L)
        val indexId = intent.getLongExtra(INDEX_ID, -1L)
        ALog.i(TAG, "Init MediaViewActivity data, type = $type, threadId = $threadId, indexId = $indexId")

        viewModel = when (type) {
            TYPE_PRIVATE, TYPE_PRIVATE_BROWSER -> ViewModelProviders.of(this).get(MediaViewPrivateViewModel::class.java)
            TYPE_GROUP, TYPE_GROUP_BROWSER -> ViewModelProviders.of(this).get(MediaViewGroupViewModel2::class.java)
            else -> ViewModelProviders.of(this).get(MediaViewHistoryViewModel::class.java)
        }

        if (type == TYPE_HISTORY) {
            val index = intent.getIntExtra(HISTORY_INDEX, 0)
            viewModel.getAllMediaData(threadId, indexId) { dataList ->
                mediaDataList.addAll(dataList)
                adapter.notifyDataSetChanged()
                chats_media_view_pager.setCurrentItem(index, false)
                setTransitionData()
            }

        } else {
            viewModel.getCurrentData(threadId, indexId) { data ->

                if (mediaDataList.isNotEmpty()) {
                    chats_media_view_pager.setCurrentItem(mediaDataList.indexOf(data), false)
                } else {
                    mediaDataList.add(data)
                    adapter.notifyDataSetChanged()
                }

                viewModel.getAllMediaData(threadId, indexId, (type == TYPE_GROUP_BROWSER || type == TYPE_PRIVATE_BROWSER)) { dataList ->
                    mediaDataList.clear()
                    mediaDataList.addAll(dataList)
                    adapter.notifyDataSetChanged()
                }

                AmeDispatcher.mainThread.dispatch({
                    setTransitionData()
                }, 100)

            }
        }
    }

    private fun initSingleResources() {
        val uri = intent.getStringExtra(MEDIA_URI)
        val data = MediaViewData(0L, Uri.parse(uri), "image/*", MEDIA_TYPE_IMAGE, Any(), MSG_TYPE_SINGLE)
        mediaDataList.add(data)
        adapter.notifyDataSetChanged()
        chats_media_more_view.visibility = View.GONE

        setTransitionData()
    }

    private inner class MediaViewPagerAdapter : CustomFragmentStatePagerAdapter<MediaViewData>(supportFragmentManager) {

        override fun getItem(position: Int): Fragment {
            val f = MediaViewFragment()
            f.setData(getItemData(position))
            f.setMasterSecret(masterSecret)
            f.setListener(fragmentListener)
            return f
        }

        override fun getItemData(position: Int): MediaViewData? {
            if (position > mediaDataList.lastIndex) {
                return null
            }
            return mediaDataList[position]
        }

        override fun getDataPosition(data: MediaViewData?): Int {
            return mediaDataList.indexOf(data)
        }

        override fun getCount(): Int {
            return mediaDataList.size
        }
    }

    override fun onBackPressed() {
        dismissActivity()
    }

    private fun setTransitionData() {
        if (enterTransitionId == 0L) {
            val f = adapter.getCurrentFragment() as? MediaViewFragment
            if (f != null) {
                val data = f.getData()
                enterTransitionId = data?.indexId ?: 0
                val transactionView = f.getTransitionView()
                if (transactionView != null) {
                    ViewCompat.setTransitionName(transactionView, "${ShareElements.Activity.MEDIA_PREIVEW}${data?.indexId}")
                }
            }
            startPostponedEnterTransition()
        }
    }

    fun dismissActivity() {
        try {
            val f = (adapter.getCurrentFragment() as? MediaViewFragment) ?: return
            val data = f.getData()
            if (data != null) {
                ViewCompat.setTransitionName(chats_media_view_pager, null)
                setEnterSharedElementCallback(object : SharedElementCallback() {
                    override fun onMapSharedElements(names: MutableList<String>?, sharedElements: MutableMap<String, View>?) {
                        names?.clear()
                        sharedElements?.clear()
                        val transitionTag = "${ShareElements.Activity.MEDIA_PREIVEW}${data.indexId}"
                        names?.add(transitionTag)
                        val view = f.getTransitionView()
                        if (view != null) {
                            ViewCompat.setTransitionName(view, transitionTag)
                            sharedElements?.put(transitionTag, view)
                        }
                    }
                })
                setResult(Activity.RESULT_OK, Intent().apply { putExtra(ShareElements.PARAM.MEDIA_INDEX, data.indexId) })
            }
            chats_media_more_view.visibility = View.GONE
        } catch (ex: Exception) {
            ALog.e(TAG, "dismissActivity error", ex)
        } finally {
            exitFullScreen()
            finishAfterTransition()
        }
    }
}