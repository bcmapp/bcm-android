package com.bcm.messenger.chats.mediabrowser.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.BaseMediaBrowserViewModel
import com.bcm.messenger.chats.mediabrowser.IMediaBrowserMenuProxy
import com.bcm.messenger.chats.mediabrowser.MediaHandleViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.chats_media_browser_activity.*
import kotlinx.android.synthetic.main.chats_media_browser_tab_item_layout.view.*


/**
 * Created by zjl on 2018/10/16.
 */
@Route(routePath = ARouterConstants.Activity.CHAT_MEDIA_BROWSER)
class MediaBrowserActivity : AccountSwipeBaseActivity() {
    companion object {
        fun router(accountContext: AccountContext, address: Address, deleteMode: Boolean = false) {
            BcmRouter.getInstance().get(ARouterConstants.Activity.CHAT_MEDIA_BROWSER)
                    .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, address)
                    .putBoolean(BROWSER_MODE, deleteMode)
                    .startBcmActivity(accountContext)
        }

        //param
        private const val BROWSER_MODE = "browser_mode"

        const val IMAGE_PAGE = 0
        const val FILE_PAGE = 1
        const val LINK_PAGE = 2

        const val SELECT_ALL = 1
        const val DESELECT_ALL = 0
        const val NONE_OBJECT = -1
    }

    private var currentPage = 0
    private lateinit var address: Address
    private var indexId = -1L
    private var isDeleteMode = false
    private var mediaFragment: MediaBrowserFragment? = null
    private var fileFragment: FileBrowserFragment? = null

    private lateinit var viewModel: MediaHandleViewModel
    private lateinit var menuProxyList: ArrayList<IMediaBrowserMenuProxy>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_media_browser_activity)

        address = intent.getParcelableExtra(ARouterConstants.PARAM.PARAM_ADDRESS)
        indexId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_INDEX_ID, -1L)
        isDeleteMode = intent.getBooleanExtra(BROWSER_MODE, false)

        initPages()
        initTabLayout()
        initSelection()

        selectBtnChange(SELECT_ALL)

        if (isDeleteMode) {
            browser_share_img.visibility = View.GONE
            browser_save_img.visibility = View.GONE
            selection_size_view.visibility = View.VISIBLE
        }

        chats_media_browser_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                if (isDeleteMode) {
                    if (media_browser_content.currentItem == 0) {
                        selectBtnChange(mediaFragment?.dillSelect())
                    } else if (media_browser_content.currentItem == 1) {
                        selectBtnChange(fileFragment?.dillSelect())
                    }
                } else {
                    viewModel.setSelecting(!viewModel.isSelecting())
                }
            }
        })

        refresh()
    }

    private fun initPages() {
        val fms = ArrayList<Fragment>()
        menuProxyList = ArrayList()

        mediaFragment = MediaBrowserFragment()
        mediaFragment?.setDeleteMode(isDeleteMode)
        mediaFragment?.selectCallBack {
            selectBtnChange(it)
        }
        var arg = Bundle()
        arg.putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, address)
        arg.putParcelable(ARouterConstants.PARAM.PARAM_MASTER_SECRET, getMasterSecret())
        arg.putLong(ARouterConstants.PARAM.PARAM_INDEX_ID, indexId)
        arg.putSerializable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
        mediaFragment?.arguments = arg
        mediaFragment?.let {
            fms.add(it)
            menuProxyList.add(it)
        }

        fileFragment = FileBrowserFragment()
        fileFragment?.setDeleteMode(isDeleteMode)
        fileFragment?.selectCallBack {
            selectBtnChange(it)
        }
        fileFragment?.arguments = arg
        fileFragment?.let {
            fms.add(it)
            menuProxyList.add(it)
        }

        val linkFragment = FileBrowserFragment()
        linkFragment.setDeleteMode(isDeleteMode)
        arg = arg.clone() as Bundle
        arg.putInt(ARouterConstants.PARAM.PARAM_BROWSER_TYPE, BaseMediaBrowserViewModel.TYPE_LINK)
        linkFragment.arguments = arg
        fms.add(linkFragment)
        menuProxyList.add(linkFragment)

        media_browser_content.adapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return fms[position]
            }

            override fun getCount(): Int {
                return fms.size
            }
        }
    }

    private fun initTabLayout() {
        val titleList = mutableListOf(
                getString(R.string.chats_browser_media_title),
                getString(R.string.chats_browser_file_title),
                getString(R.string.chats_media_link_title)
        )

        media_browser_tab.setupWithViewPager(media_browser_content)
        for (i in 0 until 3) {
            val tabView = media_browser_tab.getTabAt(i) ?: continue
            val view = layoutInflater.inflate(R.layout.chats_media_browser_tab_item_layout, tabView.view, false)
            view.media_browser_tab_text.text = titleList[i]
            if (i == 0) {
                view.media_browser_tab_text.isSelected = true
            }
            tabView.customView = view
        }
        media_browser_tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(p0: TabLayout.Tab?) {
            }

            override fun onTabUnselected(p0: TabLayout.Tab?) {
                p0?.customView?.media_browser_tab_text?.apply {
                    currentMenuProxy().active(false)
                    isSelected = false
                    setTextColor(getAttrColor(R.attr.chats_media_browser_text_unselected_color))
                }
            }

            override fun onTabSelected(p0: TabLayout.Tab?) {
                if (p0 != null && p0.position != currentPage) {
                    currentPage = p0.position
                    currentMenuProxy().active(true)
                    viewModel.setSelecting(false)
                    enableOption(browser_save_img, currentPage != 2)
                }

                p0?.customView?.media_browser_tab_text?.apply {
                    isSelected = true
                    setTextColor(getAttrColor(R.attr.chats_media_browser_text_selected_color))
                }
            }
        })
    }

    private fun initSelection() {
        viewModel = ViewModelProviders.of(this).get(MediaHandleViewModel::class.java)
        viewModel.setDeleteMode(isDeleteMode)
        viewModel.selection.observe(this, object : Observer<MediaHandleViewModel.SelectionState> {
            var selecting = false
            override fun onChanged(it: MediaHandleViewModel.SelectionState?) {
                if (null != it) {
                    if (it.selecting != selecting) {
                        selecting = it.selecting
                        selectionStateChanged(selecting)
                    }
                    if (isDeleteMode) {
                        updateSelectionFileSize(it)
                    }
                }
            }
        })

        browser_share_img.setOnClickListener {
            if (viewModel.selection.value?.selectionList?.isNotEmpty() == true) {
                if (viewModel.selection.value?.selectionList?.size ?: 0 < 15) {
                    currentMenuProxy().forward()
                } else {
                    AmeAppLifecycle.failure(getString(R.string.chats_max_forward_error), true)
                }
            } else {
                AmeAppLifecycle.failure(getString(R.string.chats_select_file_first), true)
            }
        }

        browser_delete_img.setOnClickListener {
            if (viewModel.selection.value?.selectionList?.isNotEmpty() == true) {
                currentMenuProxy().delete()
            } else {
                AmeAppLifecycle.failure(getString(R.string.chats_select_file_first), true)
            }
        }
        browser_save_img.setOnClickListener {
            if (viewModel.selection.value?.selectionList?.isNotEmpty() == true) {
                currentMenuProxy().save()
            } else {
                AmeAppLifecycle.failure(getString(R.string.chats_select_file_first), true)
            }
        }
    }

    private fun updateSelectionFileSize(it: MediaHandleViewModel.SelectionState?) {
        val size = it?.fileByteSize ?: 0
        val text = StringAppearanceUtil.formatByteSizeString(size)
        selection_size_view.text = text
    }

    private fun selectionStateChanged(selecting: Boolean) {
        if (isDeleteMode) {
            media_browser_more_layout.visibility = View.VISIBLE
            return
        }

        if (selecting) {
            chats_media_browser_title.setRightText(getString(R.string.chats_cancel))
            media_browser_more_layout.visibility = View.VISIBLE
        } else {
            chats_media_browser_title.setRightText(getString(R.string.chats_select))
            media_browser_more_layout.visibility = View.GONE
        }
    }

    private fun currentMenuProxy(): IMediaBrowserMenuProxy {
        return menuProxyList[currentPage]
    }

    private fun refresh() {

    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val newAddress = intent?.getParcelableExtra<Address>(ARouterConstants.PARAM.PARAM_ADDRESS)
        if (null != newAddress && address != newAddress) {
            address = newAddress
            refresh()
        }
    }

    fun selectBtnChange(selectStyle: Int?) {
        if (selectStyle == null) return
        if (isDeleteMode && currentPage == LINK_PAGE) {
            chats_media_browser_title.setRightText("")
            return
        }
        if (isDeleteMode) {
            when (selectStyle) {
                SELECT_ALL -> {
                    chats_media_browser_title.setRightText(getString(R.string.chats_media_deselect_all))
                }
                DESELECT_ALL -> {
                    chats_media_browser_title.setRightText(getString(R.string.chats_media_select_all))
                }
                NONE_OBJECT -> chats_media_browser_title.setRightText("")
            }
        }
    }


    private fun enableOption(image: ImageView, enable: Boolean) {
        if (enable) {
            image.clearColorFilter()
        } else {
            val greyFilter = PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY)
            image.colorFilter = greyFilter
        }
        image.isEnabled = enable
    }

    override fun onBackPressed() {
        if (viewModel.isSelecting() && !isDeleteMode) {
            viewModel.setSelecting(false)
        } else
            super.onBackPressed()
    }
}