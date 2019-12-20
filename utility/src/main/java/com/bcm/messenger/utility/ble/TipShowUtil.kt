package com.bcm.messenger.utility.ble

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface

object TipShowUtil {
    fun show(context: Context, title:String, tip:String, settingTitle:String, cancel:String, result:(goSetting:Boolean, canceled:Boolean)->Unit) {
        var finished = false
        AlertDialog.Builder(context)
                .setCancelable(false)
                .setTitle(title)
                .setMessage(tip)
                .setPositiveButton(settingTitle) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                    finished = true
                    result(true, false)
                }
                .setNegativeButton(cancel) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                    finished = true
                    result(false, true)
                }.setOnDismissListener {
                    if (!finished) {
                        finished = true
                        result(false, false)
                    }
                }
                .show()
    }
}