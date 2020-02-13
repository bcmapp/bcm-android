package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by wjh on 2018/10/08.
 */
object QrCodeReaderPopWindow {

    private val TAG = "QrCodeReaderPopWindow"

    fun createPopup(context: Context, anchorView: View, scanTag: ZoomingImageView.QRTagBean, autoDismiss: Boolean, callback: ((confirm: Boolean) -> Unit)? = null) {

        var confirm = false
        val contentView = LayoutInflater.from(context).inflate(R.layout.chats_qr_scanner_popup, null, false)
        val popupWindow = PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        popupWindow.contentView = contentView
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true
        popupWindow.isTouchable = true
        popupWindow.setBackgroundDrawable(BitmapDrawable())
        popupWindow.animationStyle = R.style.Chats_QR_Discern_PopupWindow

        popupWindow.setOnDismissListener {
            callback?.invoke(confirm)
        }

        val scanBtn = contentView.findViewById<TextView>(R.id.qr_scanner_btn)
        //val locationView = contentView.findViewById<View>(R.id.qr_location_v)
        scanBtn.setOnClickListener {
            confirm = true
            popupWindow.dismiss()
        }

        popupWindow.contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val measureW = popupWindow.contentView.measuredWidth
        val measureH = popupWindow.contentView.measuredHeight
        val dm = context.resources.displayMetrics
        ALog.d(TAG, "max width: ${dm.widthPixels}, max height:${dm.heightPixels}")
        ALog.i(TAG, "scanTag left: ${scanTag.sx}, top: ${scanTag.sy}")

        ALog.d(TAG, "measure width: ${popupWindow.contentView.measuredWidth}, height: ${popupWindow.contentView.measuredHeight}")

        val sx = (scanTag.sx - measureW / 2.0f).toInt()
        val sy = (scanTag.sy - measureH).toInt()
        if (sy >= 0) {
            when {
                sx < 0 -> popupWindow.showAtLocation(anchorView, Gravity.START or Gravity.TOP, 0,
                        sy)
                sx + measureW > dm.widthPixels -> popupWindow.showAtLocation(anchorView, Gravity.START or Gravity.TOP, (dm.widthPixels - measureW).toInt(),
                        sy)
                else -> popupWindow.showAtLocation(anchorView, Gravity.START or Gravity.TOP, sx,
                        sy)
            }
        } else {
            when {
                sx < 0 -> popupWindow.showAtLocation(anchorView, Gravity.START or Gravity.TOP, 0,
                        scanTag.sy.toInt())
                sx + measureW > dm.widthPixels -> popupWindow.showAtLocation(anchorView, Gravity.START or Gravity.TOP, (dm.widthPixels - measureW).toInt(),
                        sy)
                else -> popupWindow.showAtLocation(anchorView, Gravity.START or Gravity.TOP, sx,
                        scanTag.sy.toInt())
            }
        }

        if (autoDismiss) {
            contentView.postDelayed({
                popupWindow.dismiss()
            }, 1500)
        }
    }
}