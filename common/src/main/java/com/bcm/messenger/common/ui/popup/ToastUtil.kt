package com.bcm.messenger.common.ui.popup

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import java.lang.ref.WeakReference

/**
 * Created by bcm.social.01 on 2018/6/8.
 */
object ToastUtil {

    private var mToastRef: WeakReference<Toast>? = null

    fun show(context: Context, toast: String) {
        show(context, toast, Toast.LENGTH_SHORT)
    }

    fun show(context: Context, toast: String, duration: Int = Toast.LENGTH_SHORT) {
        var t = mToastRef?.get()
        t?.cancel()
        t = Toast.makeText(context, toast, duration)
        t.setGravity(Gravity.CENTER, 0, 0)
        t.show()

        mToastRef = WeakReference(t)
    }

}