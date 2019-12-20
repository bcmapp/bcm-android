package com.bcm.messenger.contacts.adapter

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.StickyLinearDecoration
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.contacts.R
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by wjh on 2019/7/4
 */
open class ContactsLinearDecorationCallback(private val context: Context, private val mAdapter: LinearBaseAdapter<Recipient>) : StickyLinearDecoration.StickyHeaderCallback {

    private val TAG = "ContactsStickyCallback"
    private val mHeaderMap: MutableMap<String, TextView> = mutableMapOf()

    override fun getHeaderData(pos: Int): StickyLinearDecoration.StickyHeaderData? {
        try {
            if (pos < 0 || pos >= mAdapter.itemCount) {
                return null
            }
            var pData: LinearBaseAdapter.BaseLinearData<Recipient>? = null
            var nData: LinearBaseAdapter.BaseLinearData<Recipient>? = null
            val cData = mAdapter.getMainData(pos)

            if (cData.data == null) {
                return null
            }
            val pIndex = pos -1
            if (pIndex >= 0) {
                pData = mAdapter.getMainData(pIndex)
            }
            val nIndex = pos + 1
            if (nIndex < mAdapter.itemCount) {
                nData = mAdapter.getMainData(nIndex)
            }
            ALog.d(TAG, "getHeaderData cPos: $pos, pPos: $pIndex, nPos: $nIndex, cLetter: ${cData.letter}, npLetter: ${pData?.letter}, nLetter: ${nData?.letter}")

            val isFirst = cData.letter != pData?.letter || (cData.data != null && pData.data == null)
            val isLast = cData.letter != nData?.letter || (cData.data != null && nData.data == null)
            var header = mHeaderMap[cData.letter]
            if (header == null) {
                header = TextView(context)
                header.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                header.text = cData.letter
                header.gravity = Gravity.CENTER_VERTICAL
                header.setTextColor(context.getColorCompat(R.color.common_content_second_color))
                header.textSize = 14.0f
                header.setBackgroundColor(Color.parseColor("#E5FFFFFF"))
                val w = AppContextHolder.APP_CONTEXT.resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
                val h = 8.dp2Px()
                header.setPadding(w, h, w, h)

                mHeaderMap[cData.letter] = header
            }
            ALog.d(TAG, "getHeaderData cPos: $pos, pPos: $pIndex, nPos: $nIndex, isFirst: $isFirst, isLast: $isLast, header: ${header.text}")

            return StickyLinearDecoration.StickyHeaderData(isFirst, isLast, header, 36.dp2Px(), 1.dp2Px())

        }catch (ex: Exception) {
            ALog.e(TAG, "getHeaderData error", ex)
        }
        return null
    }


}