package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.imagepicker.widget.CropRoundCornerTransform
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.core.MapApiConstants
import kotlinx.android.synthetic.main.chats_share_map_view.view.*
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.utils.getColorCompat

/**
 * Created by zjl on 2018/6/23.
 */
class MapShareView : ConstraintLayout {

    private var mContent: AmeGroupMessage.LocationContent? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        View.inflate(context, R.layout.chats_share_map_view, this)
    }


    fun setAppearance(@ColorRes mainTextColor: Int, isSendByMe: Boolean) {
        location_title?.setTextColor(context.getColorCompat(mainTextColor))
        location_address?.setTextColor(context.getColorCompat(mainTextColor))
        if (isSendByMe) {
            location_title?.setBackgroundResource(R.drawable.chats_location_sent_top_bg)
            location_address?.setBackgroundResource(R.color.common_color_379BFF)
        } else {
            location_title?.setBackgroundResource(R.drawable.chats_location_receive_top_bg)
            location_address?.setBackgroundResource(R.color.common_color_white)
        }
    }

    fun setMap(glideRequests: GlideRequests, content: AmeGroupMessage.LocationContent) {
        if (this.mContent == content) {
            return
        }
        mContent = content
        setTitleContent(content.title, content.address)
        val radius = resources.getDimensionPixelSize(R.dimen.chats_conversation_item_radius)
        glideRequests.load(getMap(context, content.latitude, content.longtitude, content.mapType))
                .transform(CropRoundCornerTransform(radius, 0, CropRoundCornerTransform.CornerType.BOTTOM))
                .into(location_map)
    }


    fun setTitleContent(title: String?, content: String?) {
        if (!title.isNullOrEmpty()) {
            location_title?.text = title
        }
        if (!content.isNullOrEmpty()) {
            location_address?.text = content
        }
    }

    private fun getMap(context: Context, latitude: Double, longitude: Double, mapType: Int): String {
        return getMap(context, latitude, longitude, mapType, 235, 86)
    }


    private fun getMap(context: Context, latitude: Double, longitude: Double, mapType: Int, width: Int, height: Int): String {
        return getMap(context, latitude, longitude, mapType, width, height, "16")
    }

    private fun getMap(context: Context, lat: Double, lon: Double, mapType: Int, width: Int, height: Int, zoom: String): String {
        var builder: StringBuilder? = null
        builder = StringBuilder(MapApiConstants.googleImgeUrl)
        builder.append("?center=").append(lat).append(",").append(lon)
        builder.append("&zoom=").append(zoom)
        builder.append("&size=").append(AppUtil.dp2Px(resources, width)).append("x").append(AppUtil.dp2Px(resources, height))
        builder.append("&markers=").append(lat).append(",").append(lon)
        builder.append("&key=").append(MapApiConstants.googlePlaceKey)

        return builder.toString()
    }
}