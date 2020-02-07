package com.bcm.messenger.me.ui.feedback

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.imagepicker.BcmPickPhotoConstants
import com.bcm.messenger.common.imagepicker.BcmPickPhotoView
import com.bcm.messenger.common.imagepicker.bean.SelectedModel
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.me_activity_help.*

/**
 * Created by bcm.social.01 on 2018/6/21.
 */
@Route(routePath = ARouterConstants.Activity.FEEDBACK)
class AmeFeedbackActivity : AccountSwipeBaseActivity() {
    private lateinit var viewModel: FeedBackViewModel
    private var screenshotAdapter: FeedBackScreenshotAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.me_activity_help)
        viewModel = ViewModelProviders.of(this).get(FeedBackViewModel::class.java)
        help_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                submit()
            }
        })
        initCategoryAdapter()
        initScreenshotAdapter()

        feedback_description.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.isNotBlank() == true) {
                    help_title_bar.setRightTextColor(getAttrColor(R.attr.common_text_blue_color))
                    help_title_bar.setRightClickable(true)
                } else {
                    help_title_bar.setRightTextColor(getAttrColor(R.attr.common_text_third_color))
                    help_title_bar.setRightClickable(false)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        feedback_description.post {
            feedback_description.requestFocus()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BcmPickPhotoConstants.PICK_PHOTO_REQUEST && data != null) {
            val selectPaths = data.getSerializableExtra(BcmPickPhotoConstants.EXTRA_PATH_LIST) as ArrayList<SelectedModel>
            selectPaths.forEach {
                handleImageSelect(mutableListOf(it.path))
            }
        }
    }

    private fun initCategoryAdapter() {
        val list = mutableListOf(
                getString(R.string.me_feedback_bug),
                getString(R.string.me_feedback_crash),
                getString(R.string.me_feedback_workflow),
                getString(R.string.me_feedback_wording),
                getString(R.string.me_feedback_suggestion),
                getString(R.string.me_feedback_others))
        viewModel.categoryText = list[0]
        val categoryAdapter = FeedBackCategoryAdapter(this, list)
        categoryAdapter.selectedPosition = 0
        feedback_category_list.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        feedback_category_list.adapter = categoryAdapter
    }

    private fun initScreenshotAdapter() {
        screenshotAdapter = FeedBackScreenshotAdapter(this) {
            selectAlbum()
        }
        feedback_srceenshot_list.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        feedback_srceenshot_list.adapter = screenshotAdapter
    }

    private fun selectAlbum() {
        val selectSize = FeedBackScreenshotAdapter.MAX_COUNT - viewModel.screenshotlist.size
        PermissionUtil.checkCamera(this) {
            if (it) {
                BcmPickPhotoView.Builder(this)
                        .setShowVideo(false)
                        .setShowGif(true)
                        .setItemSpanCount(3)
                        .setPickPhotoLimit(selectSize)
                        .build().start()
            }
        }
    }

    private fun handleImageSelect(selectPaths: MutableList<String>?) {
        screenshotAdapter?.addList(selectPaths)
    }

    private fun submit() {
        hideKeyboard()
        AmePopup.loading.show(this)
        AmeModuleCenter.user(accountContext)?.feedback(viewModel.categoryText, feedback_description.text.toString(), viewModel.screenshotlist) { result, cause ->
            AmePopup.loading.dismiss()
            if (result) {
                AmePopup.result.succeed(this, getString(R.string.me_str_succeed)) {
                    finish()
                }
            } else {
                AmePopup.result.failure(this, getString(R.string.me_str_failed))
            }
        }
    }
}