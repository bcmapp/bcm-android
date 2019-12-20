package com.bcm.messenger.common.ui

import android.app.Dialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.R
import com.bcm.messenger.common.ui.gridhelper.GridPagerSnapHelper
import com.bcm.messenger.common.ui.gridhelper.TwoRowDataTransform
import com.bcm.messenger.common.ui.gridhelper.transformAndFillEmptyData
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.dp2Px
import kotlinx.android.synthetic.main.common_share_app_item.view.*
import kotlinx.android.synthetic.main.common_share_dialog.*
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.common.utils.saveTextToBoard
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by Kin on 2019/9/23
 */
class CommonShareView {
    private val TAG = "CommonShareView"

    class Config {
        companion object {
            const val TYPE_IMAGE_JPG = "image/jpeg"
            const val TYPE_IMAGE_PNG = "image/png"
            const val TYPE_TEXT = "text/plain"
        }

        var uri: Uri? = null
        var type = "application/octet-stream"
        var text: String? = null
    }

    class Builder {
        private val config = Config()

        fun setUri(uri: Uri): Builder {
            config.uri = uri
            return this
        }

        fun setText(text: String): Builder {
            config.text = text
            return this
        }

        fun setType(type: String): Builder {
            config.type = type
            return this
        }

        fun show(activity: FragmentActivity?): CommonShareDialog? {
            if (activity == null) return null
            val dialog = CommonShareDialog()
            dialog.config = config
            activity.supportFragmentManager
                    .beginTransaction()
                    .add(dialog, "Share")
                    .commit()
            return dialog
        }
    }

    class CommonShareDialog : DialogFragment() {
        private val TAG = "CommonShareDialog"

        private val width: Int
        private val padding: Int

        var config = Config()
        val appList = filterPackages()

        init {
            width = if ((AppContextHolder.APP_CONTEXT.getScreenWidth() - 84.dp2Px() * 4) < 40.dp2Px()) {
                84.dp2Px()
            } else {
                (AppContextHolder.APP_CONTEXT.getScreenWidth() - 40.dp2Px()) / 4
            }
            padding = (AppContextHolder.APP_CONTEXT.getScreenWidth() - width * 4) / 2
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.common_share_dialog, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            updateView()
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            activity?.let {
                val dialog = Dialog(it, R.style.CommonBottomPopupWindow)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.window?.let { window ->
                    window.setBackgroundDrawableResource(android.R.color.transparent)
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.setWindowAnimations(R.style.CommonBottomPopupWindow)

                    val windowParams = window.attributes
                    windowParams.dimAmount = 0.0f
                    windowParams.gravity = Gravity.BOTTOM
                    windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
                    windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
                    window.attributes = windowParams

                    window.decorView.setPadding(0, 0, 0, 0)
                }
                return dialog
            }
            return super.onCreateDialog(savedInstanceState)
        }

        override fun onPause() {
            super.onPause()
            dismiss()
        }

        private fun updateView() {
            if (appList.size <= 8) {
                val convertAppList = if (appList.size <= 4) appList else transformAndFillEmptyData(TwoRowDataTransform(2, 4), appList)
                share_app_list.adapter = AppAdapter(convertAppList)
                share_app_list.layoutManager = GridLayoutManager(context, if (appList.size <= 4) 1 else 2, RecyclerView.HORIZONTAL, false)
                share_app_list.setPadding(padding, 5.dp2Px(), padding, 20.dp2Px())

                share_indicator.visibility = View.GONE
            } else {
                val convertAppList = transformAndFillEmptyData(TwoRowDataTransform(2, 4), appList)
                share_app_list.adapter = AppAdapter(convertAppList)
                share_app_list.layoutManager = GridLayoutManager(context, 2, RecyclerView.HORIZONTAL, false)
                share_app_list.setPadding(padding - 1, 5.dp2Px(), padding - 1, 0)
                share_app_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            val index = (share_app_list.layoutManager as GridLayoutManager).findFirstCompletelyVisibleItemPosition()
                            share_indicator.setCurrentIndicator(index / 8)
                        }
                    }
                })
                GridPagerSnapHelper().apply {
                    setRow(2)
                    setColumn(4)
                    attachToRecyclerView(share_app_list)
                }
                share_indicator.setIndicators(convertAppList.size / 8)
            }


            share_cancel.setOnClickListener {
                dismiss()
            }
        }

        private fun shareToApp(resolveInfo: ResolveInfo) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                    data = config.uri
                    type = config.type
                    putExtra(Intent.EXTRA_TEXT, config.text)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                AppContextHolder.APP_CONTEXT.startActivity(shareIntent)
            } catch (tr: Throwable) {
                ALog.w(TAG, "Cannot launch ${resolveInfo.activityInfo.packageName}. ${tr.message}")
            } finally {
                dismiss()
            }
        }

        private fun openSystemShare() {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    data = config.uri
                    type = config.type
                    putExtra(Intent.EXTRA_TEXT, config.text)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                AppContextHolder.APP_CONTEXT.startActivity(intent)
            } catch (tr: Throwable) {
                ALog.w(TAG, "Cannot launch system share. ${tr.message}")
            } finally {
                dismiss()
            }
        }

        private fun shareToSms() {
            try {
                val uri = Uri.parse("smsto:")
                val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                    putExtra("sms_body", config.text)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                AppContextHolder.APP_CONTEXT.startActivity(intent)
            } catch (tr: Throwable) {
                ALog.w(TAG, "Cannot launch sms app. ${tr.message}")
            } finally {
                dismiss()
            }
        }

        private fun shareToEmail() {
            try {
                val uri = Uri.parse("mailto:")
                val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                    putExtra(Intent.EXTRA_TEXT, config.text)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                AppContextHolder.APP_CONTEXT.startActivity(intent)
            } catch (tr: Throwable) {
                ALog.w(TAG, "Cannot launch email app. ${tr.message}")
            } finally {
                dismiss()
            }
        }

        private fun filterPackages(): List<ResolveInfo> {
            val targetApps = mutableListOf<ResolveInfo>()
            targetApps.add(ResolveInfo().apply { resolvePackageName = "copy" })

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
            }
            val packageList = AppContextHolder.APP_CONTEXT.packageManager.queryIntentActivities(intent, 0)
            packageList.forEach {
                if (matchApps(it)) {
                    targetApps.add(it)
                }
            }

            targetApps.add(ResolveInfo().apply { resolvePackageName = "sms" })
            targetApps.add(ResolveInfo().apply { resolvePackageName = "email" })
            targetApps.add(ResolveInfo().apply { resolvePackageName = "more" })

            return targetApps
        }

        private fun matchApps(resolveInfo: ResolveInfo): Boolean {
            val packageName = resolveInfo.activityInfo.packageName
            return (packageName == "com.tencent.mm" && resolveInfo.activityInfo.name != "com.tencent.mm.ui.tools.AddFavoriteUI") || // WeChat
                    packageName == "com.facebook.orca" || // Messenger
                    packageName == "com.whatsapp" || // WhatsApp
                    packageName == "org.telegram.messenger" || // Telegram
                    packageName == "org.thunderdog.challegram" || // TelegramX
                    packageName == "com.snapchat.android" || // Snapchat
//                    (packageName == "com.facebook.katana" &&
//                            resolveInfo.activityInfo.name != "com.facebook.composer.shareintent.ImplicitShareIntentHandlerDefaultAliasCustomization" &&
//                            resolveInfo.activityInfo.name != "com.facebook.composer.shareintent.ImplicitShareIntentHandlerDefaultAliasActionClarity") || // Facebook(Remove temporary)
                    packageName == "com.twitter.android" || // Twitter
                    packageName == "com.kaokao.talk" || // Kaokao Talk
                    packageName == "jp.naver.line.android" || // Line
                    packageName == "com.sina.weibo" // Weibo
        }

        private inner class AppAdapter(private val dataList: List<ResolveInfo?>) : RecyclerView.Adapter<AppViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
                val view = layoutInflater.inflate(R.layout.common_share_app_item, parent, false)
                return AppViewHolder(view)
            }

            override fun getItemCount(): Int {
                return dataList.size
            }

            override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
                holder.setAppInfo(dataList[position])
            }
        }

        private inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            init {
                itemView.layoutParams = itemView.layoutParams.apply {
                    width = this@CommonShareDialog.width
                }
            }

            fun setAppInfo(resolveInfo: ResolveInfo?) {
                when {
                    resolveInfo == null -> {
                        itemView.app_icon.setImageDrawable(null)
                        itemView.app_name.text = ""
                        itemView.isClickable = false
                    }
                    resolveInfo.resolvePackageName == "sms" -> {
                        itemView.app_icon.setImageResource(R.drawable.common_share_sms)
                        itemView.app_name.text = getString(R.string.common_invite_share_sms)
                        itemView.isClickable = true
                        itemView.setOnClickListener {
                            shareToSms()
                        }
                    }
                    resolveInfo.resolvePackageName == "email" -> {
                        itemView.app_icon.setImageResource(R.drawable.common_share_email)
                        itemView.app_name.text = getString(R.string.common_invite_share_email)
                        itemView.isClickable = true
                        itemView.setOnClickListener {
                            shareToEmail()
                        }
                    }
                    resolveInfo.resolvePackageName == "copy" -> {
                        itemView.app_icon.setImageResource(R.drawable.common_share_copy)
                        itemView.app_name.text = getString(R.string.common_invite_share_copy)
                        itemView.isClickable = true
                        itemView.setOnClickListener {
                            AppContextHolder.APP_CONTEXT.saveTextToBoard(config.text.orEmpty())
                            AmePopup.result.succeed(activity, getString(R.string.common_copied), true)
                            dismiss()
                        }
                    }
                    resolveInfo.resolvePackageName == "more" -> {
                        itemView.app_icon.setImageResource(R.drawable.common_share_more)
                        itemView.app_name.text = getString(R.string.common_invite_share_more)
                        itemView.isClickable = true
                        itemView.setOnClickListener {
                            openSystemShare()
                        }
                    }
                    else -> {
                        val appInfo = when (resolveInfo.activityInfo.packageName) {
                            "com.tencent.mm" -> Pair(R.drawable.common_share_wechat, getString(R.string.common_invite_share_wechat))
                            "com.facebook.orca" -> Pair(R.drawable.common_share_messenger, "Messenger")
                            "com.whatsapp" -> Pair(R.drawable.common_share_whatsapp, "WhatsApp")
                            "org.telegram.messenger" -> Pair(R.drawable.common_share_telegram, "Telegram")
                            "org.thunderdog.challegram" -> Pair(R.drawable.common_share_telegram_x, "TelegramX")
                            "com.snapchat.android" -> Pair(R.drawable.common_share_snapchat, "Snapchat")
                            "com.facebook.katana" -> Pair(R.drawable.common_share_facebook, "Facebook")
                            "com.twitter.android" -> {
                                if (resolveInfo.activityInfo.name == "com.twitter.composer.ComposerActivity") {
                                    Pair(R.drawable.common_share_twitter, getString(R.string.common_invite_share_twitter_tweet))
                                } else {
                                    Pair(R.drawable.common_share_twitter, getString(R.string.common_invite_share_twitter_direct_message))
                                }
                            }
                            "com.kaokao.talk" -> Pair(R.drawable.common_share_kaokao, "Kaokao Talk")
                            "jp.naver.line.android" -> {
                                if (resolveInfo.activityInfo.name == "com.linecorp.linekeep.ui.KeepSaveActivity") {
                                    Pair(R.drawable.common_share_line, "LINE Keep")
                                } else {
                                    Pair(R.drawable.common_share_line, "LINE")
                                }
                            }
                            "com.sina.weibo" -> Pair(R.drawable.common_share_weibo, getString(R.string.common_invite_share_weibo))
                            else -> Pair(0, "")
                        }
                        itemView.app_icon.setImageResource(appInfo.first)
                        itemView.app_name.text = appInfo.second
                        itemView.isClickable = true
                        itemView.setOnClickListener {
                            shareToApp(resolveInfo)
                        }
                    }
                }
            }
        }
    }
}