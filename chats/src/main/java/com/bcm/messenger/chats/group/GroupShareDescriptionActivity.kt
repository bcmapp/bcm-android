package com.bcm.messenger.chats.group

import android.os.Bundle
import android.text.SpannableStringBuilder
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.provider.GroupModuleImp
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.BcmShareCodeStatus
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_activity_group_share_code_description.*
import java.util.concurrent.TimeUnit

/**
 *
 * Created by wjh on 2019/6/15
 */
@Route(routePath = ARouterConstants.Activity.GROUP_SHARE_DESCRIPTION)
class GroupShareDescriptionActivity : SwipeBaseActivity() {

    private val TAG = "GroupShareDescription"
    private var mShareContent: AmeGroupMessage.GroupShareContent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_group_share_code_description)

        init()
    }

    private fun init() {
        val shareContentString = intent.getStringExtra(ARouterConstants.PARAM.GROUP_SHARE.GROUP_SHARE_CONTENT) ?: ""
        ALog.d(TAG, "receive shareContentString: $shareContentString")

        if (shareContentString.isEmpty()) {
            finish()
            return
        }
        val shareContent = AmeGroupMessage.GroupShareContent.fromJson(shareContentString)
        mShareContent = shareContent

        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }
        })

        group_share_join_btn.setOnClickListener {
            try {
//                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mShareContent?.toBcmSchemeUrl()))
//                startActivity(intent)
                val eKey = shareContent.ekey
                val eKeyByteArray = if (!eKey.isNullOrEmpty()) {
                    try {
                        eKey.base64Decode()
                    } catch(e:Throwable) {
                        null
                    }
                } else {
                    null
                }
                GroupModuleImp().doGroupJoin(this, shareContent.groupId, shareContent.groupName, shareContent.groupIcon,
                        shareContent.shareCode, shareContent.shareSignature, shareContent.timestamp, eKeyByteArray) { success ->
                    if (success) {
                        delayFinish()
                    }
                }
            }catch (ex: Exception) {
                ALog.e(TAG, "group join action error", ex)
            }
        }

        group_share_shade.showLoading()
        GroupLogic.checkShareCodeStatus(shareContent.groupId, shareContent.shareCode, shareContent.shareSignature) {succeed, status ->
            group_share_shade.hide()
            if (succeed && (status == BcmShareCodeStatus.INVALID_CODE || status == BcmShareCodeStatus.NOT_EXIST)) {
                updateDescription(false)
            } else {
                updateDescription(true)
            }
        }
    }


    private fun updateDescription(isValid: Boolean) {
        if (isValid) {
            GlideApp.with(this).load(mShareContent?.groupIcon)
                    .placeholder(R.drawable.common_group_default_logo)
                    .error(R.drawable.common_group_default_logo)
                    .into(group_share_logo)

            val groupName = if (mShareContent?.groupName.isNullOrEmpty()) {
                getString(R.string.common_chats_group_default_name)
            }else {
                mShareContent?.groupName ?: ""
            }
            val builder = SpannableStringBuilder(groupName)
            builder.append("\n\n")
            builder.append(StringAppearanceUtil.applyAppearance(mShareContent?.toOldLink() ?: "", color = getColorCompat(R.color.common_app_primary_color)))
            group_share_name_tv.text = builder

            val notice = StringAppearanceUtil.applyFilterAppearanceIgnoreCase(getString(R.string.chats_group_share_description_notice), "BCM", color = getColorCompat(R.color.common_app_primary_color))
            group_share_notice_tv.text = notice

            group_share_join_btn.setTextColor(getColorCompat(R.color.common_color_white))
            group_share_join_btn.setBackgroundResource(R.drawable.common_blue_bg)
            group_share_join_btn.isEnabled = true

        }else {
            group_share_logo.setImageResource(R.drawable.common_group_default_logo)
            group_share_name_tv.text = StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_share_description_invalid_text), color = getColorCompat(R.color.common_content_warning_color))
            group_share_join_btn.setTextColor(getColorCompat(R.color.common_disable_color))
            group_share_join_btn.setBackgroundResource(R.drawable.common_grey_bg)
            group_share_join_btn.isEnabled = false
        }
    }

    private fun delayFinish() {
        Observable.just(1).delay(2, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    finish()
                }, {

                })

    }
}