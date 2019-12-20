package com.bcm.messenger.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.PlayStoreUtil
import java.lang.ref.WeakReference


/**
 * Created by bcm.social.01 on 2018/7/14.
 */
class UpgradeOptionView : AppCompatTextView {
    companion object {
        var cancelClick: WeakReference<OnClickListener?>? = null
        var cancelView: WeakReference<View?>? = null
    }

    constructor(context: Context) : this(context, null) {}
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {}
    constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style) {
        AmePopup.loading.dismiss()
    }

    override fun setOnClickListener(l: OnClickListener?) {
        if (this@UpgradeOptionView.tag == "beta_cancel_button") {
            cancelClick = WeakReference(l)
            cancelView = WeakReference(this)
        }

        super.setOnClickListener {
            if (this@UpgradeOptionView.tag == "beta_cancel_button") {
                l?.onClick(it)
            } else {
                if (AppUtil.isReleaseBuild() && AppUtil.isSupportGooglePlay()){
                    cancelClick?.get()?.onClick(cancelView?.get())
                    PlayStoreUtil.goToPlayStore(context)
                }
                else {
                    l?.onClick(it)
                }
            }
        }
    }
}