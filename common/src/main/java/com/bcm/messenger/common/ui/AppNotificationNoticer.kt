package com.bcm.messenger.common.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import com.bcm.messenger.common.R
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.common_system_notification_layout.view.*


class AppNotificationNoticer : ConstraintLayout {

    private var mShowSystemNotificationNotice = true

    companion object {

        private val mNoticerList: MutableList<AppNotificationNoticer> = mutableListOf()

        
        private fun setNoticeClose() {
            mNoticerList.forEach {
                it.mShowSystemNotificationNotice = false
                it.visibility = View.GONE
            }
        }
    }

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {
        LayoutInflater.from(context).inflate(R.layout.common_system_notification_layout, this)
        mShowSystemNotificationNotice = TextSecurePreferences.getBooleanPreference(AMELogin.majorContext, TextSecurePreferences.SYS_NOTIFICATION_NOTICE, true)
        initView()

        if (background == null) {
            setBackgroundColor(context.getColorCompat(R.color.common_background_color))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mNoticerList.add(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mNoticerList.remove(this)
    }

    private fun initView() {
        system_notification_btn.setOnClickListener {
            ALog.d("SettingActivity", "uri: ${Uri.fromParts("package", context.packageName, null)}")
            try {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })

                }else {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        system_notification_close.setOnClickListener {
            mShowSystemNotificationNotice = false
            TextSecurePreferences.setBooleanPreference(AMELogin.majorContext, TextSecurePreferences.SYS_NOTIFICATION_NOTICE, false)
            setNoticeClose()
        }
    }

    
    fun checkNotice() {
        val manager = NotificationManagerCompat.from(context)
        if(manager.areNotificationsEnabled() || !mShowSystemNotificationNotice) {
            this.visibility = View.GONE
        }else {
            this.visibility = View.VISIBLE
        }
    }
}