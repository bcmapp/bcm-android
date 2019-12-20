package com.bcm.messenger.me.utils

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.me_force_update_download_dialog.*

/**
 * Created by Kin on 2019/8/15
 */
class ForceUpdateDownloadDialog : DialogFragment() {

    companion object {

        fun show(activity: FragmentActivity?, title: String, content: String, clickInstall: View.OnClickListener): ForceUpdateDownloadDialog? {
            if (activity == null || activity.isFinishing) return null
            try{
                val dialog = ForceUpdateDownloadDialog()
                dialog.title = title
                dialog.content = content
                dialog.installListener = clickInstall
                dialog.isCancelable = false
                dialog.show(activity.supportFragmentManager, activity.javaClass.simpleName)
                return dialog

            }catch (ex: Exception) {
                ALog.e("ForceUpdateDownloadDialog", "showForceUpdateDownloadDialog error", ex)
                return null
            }
        }
    }

    private var title = ""
    private var content = ""
    private var installListener: View.OnClickListener? = null

    private var downloadFailed = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        activity?.let {
            val dialog = Dialog(it, R.style.CommonCenterPopupWindow)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
            return dialog
        }

        return super.onCreateDialog(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_force_update_download_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        upgrade_download_title.text = title
        upgrade_download_content.text = content
        upgrade_download_install.setOnClickListener(installListener)
    }

    fun updateProgress(progress: Int) {
        AmeDispatcher.mainThread.dispatch {
            upgrade_download_progress?.progress = progress
            upgrade_download_progress_text?.text = "$progress%"
        }
    }

    fun setSuccess() {
        AmeDispatcher.mainThread.dispatch {
            upgrade_download_install?.isEnabled = true
        }
    }

    fun setFailed() {
        downloadFailed = true
    }

    fun setDismissCallback(callback: DialogInterface.OnDismissListener) {
        dialog?.setOnDismissListener(callback)
    }

    override fun dismiss() {
        if (downloadFailed) super.dismiss()
        else return
    }
}