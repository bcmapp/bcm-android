package com.bcm.messenger.me.utils

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.utils.exitApp
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.me_force_update_dialog.*

/**
 * Created by Kin on 2019/8/15
 */
class ForceUpdateDialog : DialogFragment() {

    companion object {
        fun show(activity: FragmentActivity?, title: String, content: String, clickInstall: View.OnClickListener): ForceUpdateDialog? {
            if (activity == null || activity.isFinishing) return null
            return try{
                val dialog = ForceUpdateDialog()
                dialog.title = title
                dialog.content = content
                dialog.installListener = clickInstall
                dialog.isCancelable = false
                dialog.show(activity.supportFragmentManager, activity.javaClass.simpleName)
                dialog
            } catch (ex: Exception) {
                ALog.e("ForceUpdateDownloadDialog", "showForceUpdateDownloadDialog error", ex)
                null
            }
        }
    }

    private var title = ""
    private var content = ""
    private var installListener: View.OnClickListener? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        activity?.let {
            val dialog = Dialog(it, R.style.Theme_AppCompat_Light_Dialog)
            dialog.window?.let { window ->
                val windowParams = window.attributes
                windowParams.gravity = Gravity.CENTER
                window.attributes = windowParams
            }

            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
            return dialog
        }

        return super.onCreateDialog(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_force_update_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        force_upgrade_title.text = title
        force_upgrade_content.text = content
        force_upgrade_update.setOnClickListener(installListener)
        force_upgrade_exit.setOnClickListener {
            exitApp()
        }
    }

    fun setDismissCallback(callback: DialogInterface.OnDismissListener) {
        dialog?.setOnDismissListener(callback)
    }

    override fun dismiss() {
        return
    }
}