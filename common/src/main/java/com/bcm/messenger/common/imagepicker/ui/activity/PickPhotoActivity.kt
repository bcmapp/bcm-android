package com.bcm.messenger.common.imagepicker.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentTransaction
import com.bcm.messenger.common.imagepicker.BcmPickHelper
import com.bcm.messenger.common.imagepicker.BcmPickPhotoConstants
import com.bcm.messenger.common.imagepicker.PICK_TAG
import com.bcm.messenger.common.imagepicker.bean.PickPhotoChangeEvent
import com.bcm.messenger.common.imagepicker.bean.PickPhotoFinishEvent
import com.bcm.messenger.common.imagepicker.bean.PickPhotoListChangeEvent
import com.bcm.messenger.common.imagepicker.ui.GridFragment
import com.bcm.messenger.common.imagepicker.ui.ListFragment
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.ui.CommonTitleBar2
import kotlinx.android.synthetic.main.common_activity_pick_photo.*
import com.bcm.messenger.common.R

/**
 * Created by Kin on 2019/4/17
 */

private const val MODE_GRID = 1
private const val MODE_LIST = 0
class PickPhotoActivity : BasePickActivity() {
    private val TAG = "PickPhotoActivity"

    private val config = BcmPickHelper.currentPickConfig

    private lateinit var gridFragment: GridFragment
    private lateinit var listFragment: ListFragment

    private var mode = MODE_GRID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.common_activity_pick_photo)


        initView()
        initData()
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.unSubscribe(PICK_TAG)
    }

    private fun initView() {
        pick_photo_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickCenter() {
                switchFragment()
            }

            override fun onClickRight() {
                selectFinish()
            }
        })
        pick_photo_title_bar.setCenterText(getString(R.string.common_all_photos))
        val rightText = if (config.cropPhoto || !config.multiSelect) {
            ""
        } else {
            "${BcmPickHelper.currentPickConfig.applyText}(${BcmPickHelper.selectedPhotos.size})"
        }
        pick_photo_title_bar.setRightText(rightText)

        gridFragment = GridFragment()
        listFragment = ListFragment()

        showGridFragment()
        showListFragment()
        switchFragment()
    }

    fun changeDir(newDir: String) {
        switchFragment()
        BcmPickHelper.changeCurrentList(newDir)
    }

    private fun switchFragment() {
        if (mode == MODE_LIST) {
            // 
            ALog.d(TAG, "Switch to folder fragment")
            supportFragmentManager.beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .show(listFragment).hide(gridFragment).commit()
            mode = MODE_GRID
        } else {
            // 
            ALog.d(TAG, "Switch to photo fragment")
            supportFragmentManager.beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .show(gridFragment).hide(listFragment).commit()
            mode = MODE_LIST
        }
    }

    private fun showGridFragment() {
        supportFragmentManager.beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                .add(R.id.pick_photo_content_layout, gridFragment)
                .commit()
    }

    private fun showListFragment() {
        supportFragmentManager.beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .add(R.id.pick_photo_content_layout, listFragment)
                .commit()
    }

    private fun initData() {
        RxBus.subscribe<Any>(PICK_TAG) {
            when (it) {
                is PickPhotoListChangeEvent -> {
                    // 
                    ALog.i(TAG, "Receive list change event")
                    pick_photo_title_bar.setCenterText(it.newDir)
                }
                is PickPhotoFinishEvent -> {
                    // 
                    ALog.i(TAG, "Receive finish event")
                    selectFinish()
                }
                is PickPhotoChangeEvent -> {
                    // 
                    ALog.i(TAG, "Receive selected list change event")
                    pick_photo_title_bar.setRightText("${BcmPickHelper.currentPickConfig.applyText}(${BcmPickHelper.selectedPhotos.size})")
                }
            }
        }

        BcmPickHelper.startQuery(config.showGif, config.showVideo)
    }

    private fun selectFinish() {
        if (BcmPickHelper.selectedPhotos.isEmpty()) return
        ALog.i(TAG, "Select finish, return data")
        val intent = Intent()
        intent.putExtra(BcmPickPhotoConstants.EXTRA_PATH_LIST, (BcmPickHelper.selectedPhotos) as ArrayList)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}