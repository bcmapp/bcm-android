package com.bcm.messenger.common.imagepicker.ui.activity

import android.content.res.Configuration
import android.os.Bundle
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.bcm.messenger.common.imagepicker.BcmPickHelper
import com.bcm.messenger.common.imagepicker.PICK_TAG
import com.bcm.messenger.common.imagepicker.PREVIEW_TAG
import com.bcm.messenger.common.imagepicker.bean.MediaModel
import com.bcm.messenger.common.imagepicker.bean.PickPhotoChangeEvent
import com.bcm.messenger.common.imagepicker.bean.PickPhotoFinishEvent
import com.bcm.messenger.common.imagepicker.widget.PreviewImage
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.common.ui.CommonTitleBar2
import kotlinx.android.synthetic.main.common_activity_pick_photo_preview.*
import com.bcm.messenger.common.R

/**
 * Created by Kin on 2019/4/17
 */
class PickPhotoPreviewActivity : BasePickActivity() {
    private val TAG = "PickPhotoPreviewActivity"

    private val allImages: List<MediaModel> by lazy { initImages() }
    private val imageViews: List<PreviewImage> by lazy { initImageViews() }
    private val config = BcmPickHelper.currentPickConfig
    private var index = 0
    private var isFull = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.common_activity_pick_photo_preview)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)


        index = intent.getIntExtra("index", 0)
        RxBus.subscribe<Any>(PREVIEW_TAG) {
            when (it) {
                is PickPhotoChangeEvent -> {
                    val rightText = if (config.cropPhoto || !config.multiSelect) {
                        ""
                    } else {
                        "${BcmPickHelper.currentPickConfig.applyText}(${BcmPickHelper.selectedPhotos.size})"
                    }
                    pick_photo_preview_title_bar.setRightText(rightText)
                }
            }
        }

        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.unSubscribe(PREVIEW_TAG)
    }

    private fun initView() {
        pick_photo_preview_title_bar.setCenterText("${index + 1} / ${allImages.size}")
        pick_photo_preview_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                ALog.i(TAG, "Select complete, post finish event")
                RxBus.post(PICK_TAG, PickPhotoFinishEvent())
                finish()
            }
        })

        pick_photo_view_pager.adapter = ListPageAdapter()
        pick_photo_view_pager.currentItem = index
        pick_photo_view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                index = position
                pick_photo_preview_title_bar.setCenterText("${index + 1} / ${allImages.size}")
            }
        })
    }

    private fun initImageViews(): List<PreviewImage> = arrayListOf<PreviewImage>().apply {
        for (index in 1..4) {
            add(PreviewImage(this@PickPhotoPreviewActivity))
        }
    }

    private fun initImages(): List<MediaModel> {
        val list = mutableListOf<MediaModel>()
        list.addAll(BcmPickHelper.currentSelectableList)
        return list
    }

    private inner class ListPageAdapter : PagerAdapter() {

        override fun getCount(): Int {
            return allImages.size
        }

        override fun isViewFromObject(view: View, any: Any): Boolean {
            return view === any
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val i = position % 4
            val pic = imageViews[i]
            val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val model = allImages[position]
            pic.setImage(model, position) {
                switchFullscreen()
            }
            container.addView(pic, params)
            return pic
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            val i = position % 4
            val imageView = imageViews[i]
            container.removeView(imageView)
            imageView.clear()
        }
    }

    fun switchFullscreen() {
        isFull = !isFull
        pick_photo_preview_title_bar.animate().translationY(if (!isFull) 0f else -pick_photo_preview_title_bar.height.toFloat())
                .setInterpolator(DecelerateInterpolator())
                .start()
        if (isFull) {
            window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                window.setStatusBarLightMode()
            }
        }
    }
}