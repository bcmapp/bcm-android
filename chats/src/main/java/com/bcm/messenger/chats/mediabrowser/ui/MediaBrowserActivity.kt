package com.bcm.messenger.chats.mediabrowser.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager.widget.ViewPager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.BaseMediaBrowserViewModel
import com.bcm.messenger.chats.mediabrowser.IMediaBrowserMenuProxy
import com.bcm.messenger.chats.mediabrowser.MediaHandleViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_media_browser_activity.*


/**
 * Created by zjl on 2018/10/16.
 */
@Route(routePath = ARouterConstants.Activity.CHAT_MEDIA_BROWSER)
class MediaBrowserActivity : SwipeBaseActivity() {
    companion object {
        fun router(address:Address, deleteMode:Boolean = false){
            BcmRouter.getInstance().get(ARouterConstants.Activity.CHAT_MEDIA_BROWSER)
                    .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, address)
                    .putBoolean(BROWSER_MODE, deleteMode)
                    .navigation()
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

    private var currentPage = -1
    private lateinit var address:Address
    private var indexId = -1L
    private var isDeleteMode = false
    private var mediaFragment: MediaBrowserFragment? = null
    private var fileFragment: FileBrowserFragment? = null

    private lateinit var viewModel: MediaHandleViewModel
    private lateinit var menuProxyList:ArrayList<IMediaBrowserMenuProxy>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_media_browser_activity)

        address = intent.getParcelableExtra(ARouterConstants.PARAM.PARAM_ADDRESS)
        indexId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_INDEX_ID, -1L)
        isDeleteMode = intent.getBooleanExtra(BROWSER_MODE, false)

        initPages()
        initSelection()

        media_browser_media_title.setOnClickListener{
            showMediaPager()
        }

        media_browser_file_title.setOnClickListener{
            showFilePager()
        }

        media_browser_link_title.setOnClickListener {
            showLinkPager()
        }
        selectBtnChange(SELECT_ALL)

        if (isDeleteMode){
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

        showMediaPager()

        refresh()
    }

    private fun initPages() {
        media_browser_content.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

            }

            override fun onPageSelected(position: Int) {
                showPage(position)
            }

            override fun onPageScrollStateChanged(state: Int) {

            }
        })

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
        media_browser_content.isSlidingEnable = false
    }

    private fun initSelection() {
        viewModel = ViewModelProviders.of(this).get(MediaHandleViewModel::class.java)
        viewModel.setDeleteMode(isDeleteMode)
        viewModel.selection.observe(this, object : Observer<MediaHandleViewModel.SelectionState> {
            var selecting = false
            override fun onChanged(it: MediaHandleViewModel.SelectionState?) {
                if(null != it){
                    if (it.selecting != selecting){
                        selecting = it.selecting
                        selectionStateChanged(selecting)
                    }
                    if (isDeleteMode){
                        updateSelectionFileSize(it)
                    }
                }
            }

        })

        browser_share_img.setOnClickListener {
            if (viewModel.selection.value?.selectionList?.isNotEmpty() == true){
                if(viewModel.selection.value?.selectionList?.size?:0 < 15){
                    currentMenuProxy().forward()
                }
                else {
                    AmeAppLifecycle.failure(getString(R.string.chats_max_forward_error), true)
                }
            } else {
                AmeAppLifecycle.failure(getString(R.string.chats_select_file_first), true)
            }
        }

        browser_delete_img.setOnClickListener {
            if (viewModel.selection.value?.selectionList?.isNotEmpty() == true){
                currentMenuProxy().delete()
            }
            else {
                AmeAppLifecycle.failure(getString(R.string.chats_select_file_first), true)
            }
        }
        browser_save_img.setOnClickListener {
            if (viewModel.selection.value?.selectionList?.isNotEmpty() == true){
                currentMenuProxy().save()
            }
            else {
                AmeAppLifecycle.failure(getString(R.string.chats_select_file_first), true)
            }
        }
    }

    private fun updateSelectionFileSize(it: MediaHandleViewModel.SelectionState?) {
        val size = it?.fileByteSize?:0
        val text = StringAppearanceUtil.formatByteSizeString(size)
        selection_size_view.text = text
    }

    private fun selectionStateChanged(selecting: Boolean) {
        if (isDeleteMode){
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

    private fun refresh(){

    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val newAddress = intent?.getParcelableExtra<Address>(ARouterConstants.PARAM.PARAM_ADDRESS)
        if (null != newAddress && address != newAddress){
            address = newAddress
            refresh()
        }
    }

    private fun showMediaPager(){
        showPage(IMAGE_PAGE)
    }

    private fun showFilePager() {
        showPage(FILE_PAGE)
    }

    private fun showLinkPager() {
        showPage(LINK_PAGE)
    }

    private fun getTitleView(page:Int): TextView {
        return when(page){
            IMAGE_PAGE -> media_browser_media_title
            FILE_PAGE ->media_browser_file_title
            else -> media_browser_link_title
        }
    }

    private fun showPage(pageIndex: Int) {
        if (currentPage == pageIndex) {
            return
        }

        selectBtnChange(SELECT_ALL)

        if (currentPage >= 0) {
            val titleView = getTitleView(currentPage)
            titleView.isSelected = false
            titleView.setTextColor(getColorCompat(R.color.common_color_black))
            currentMenuProxy().active(false)
        }

        currentPage = pageIndex
        media_browser_content.setCurrentItem(currentPage, true)
        currentMenuProxy().active(true)
        val titleView = getTitleView(currentPage)
        titleView.isSelected = true
        titleView.setTextColor(getColorCompat(R.color.common_color_white))

        enableOption(browser_save_img, currentPage != LINK_PAGE)

        viewModel.setSelecting(false)
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


    private fun enableOption(image: ImageView, enable:Boolean) {
        if (enable){
            image.clearColorFilter()
        } else {
            val greyFilter = PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY)
            image.colorFilter = greyFilter
        }
        image.isEnabled = enable
    }

    override fun onBackPressed() {
        if (viewModel.isSelecting() && !isDeleteMode ) {
            viewModel.setSelecting(false)
        } else
            super.onBackPressed()
    }
}