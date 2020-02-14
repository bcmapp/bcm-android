package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.TypedValue
import android.view.*
import android.widget.*
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ConversationItemPopWindow.ItemPopWindowBuilder.Companion.TYPE_COPY
import com.bcm.messenger.chats.components.ConversationItemPopWindow.ItemPopWindowBuilder.Companion.TYPE_DELETE
import com.bcm.messenger.chats.components.ConversationItemPopWindow.ItemPopWindowBuilder.Companion.TYPE_FORWARD
import com.bcm.messenger.chats.components.ConversationItemPopWindow.ItemPopWindowBuilder.Companion.TYPE_PIN
import com.bcm.messenger.chats.components.ConversationItemPopWindow.ItemPopWindowBuilder.Companion.TYPE_REPLY
import com.bcm.messenger.chats.components.ConversationItemPopWindow.ItemPopWindowBuilder.Companion.TYPE_SELECT
import com.bcm.messenger.chats.components.ConversationItemPopWindow.ItemPopWindowBuilder.Companion.TYPE_UNSEND
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.common.utils.AppUtil
import java.lang.ref.WeakReference


/**
 *
 * Created by wjh
 */
class ConversationItemPopWindow private constructor(context: Context, anchorView: View, private val isOutgoing: Boolean, private val popWindowClickListener: PopWindowClickListener?, private val itemList: List<TextView>) {

    companion object {

        private var currentPop: WeakReference<ConversationItemPopWindow>? = null

        private fun setCurrentPop(pop: ConversationItemPopWindow?) {
            currentPop = if(pop == null) {
                null
            }else {
                WeakReference(pop)
            }
        }

        fun dismissConversationPop() {
            currentPop?.get()?.dismiss()
        }

        fun isConversationPopShowing(): Boolean {
            return currentPop?.get()?.popupWindow?.isShowing ?: false
        }
    }

    private lateinit var popupWindow: PopupWindow

    init {
        val contentView = LayoutInflater.from(context).inflate(R.layout.chats_conversation_item_popup, null, false)

        initItemList(context, contentView as? ViewGroup)
        initFloatPop(context, anchorView, contentView)

        setCurrentPop(this)
    }

    private fun getLineMaxNum(): Int {
        val screenW = AppUtil.getScreenWidth(AppContextHolder.APP_CONTEXT)
        val gap = AppUtil.dp2Px(AppContextHolder.APP_CONTEXT.resources, 20)
        val size = AppUtil.dp2Px(AppContextHolder.APP_CONTEXT.resources, 40)
        val contentW = screenW - gap - gap - gap - gap
        val num = contentW / size
        return if(contentW % size == 0) {
            num -1
        }else {
            num - 2
        }
    }

    private fun initItemList(context: Context, contentView: ViewGroup?) {
        if(contentView == null) {
            return
        }
        val max = getLineMaxNum()
        if(itemList.size > max) {
            val previousList = itemList.subList(0, max - 1)
            val nextList = itemList.subList(max - 1, itemList.size)
            previousList.forEach {
                contentView.addView(it)
            }
            val switch = createSwitchViews(context)
            contentView.addView(switch.second)

            switch.first.setOnClickListener {
                contentView.removeAllViews()
                previousList.forEach {
                    contentView.addView(it)
                }
                contentView.addView(switch.second)
            }
            switch.second.setOnClickListener {
                contentView.removeAllViews()
                contentView.addView(switch.first)
                nextList.forEach {
                    contentView.addView(it)
                }
            }

        }else {
            itemList.forEach {
                contentView.addView(it)
            }
        }
        itemList.forEach {
            it.setOnClickListener {
                when(it.tag as Int) {
                    TYPE_COPY -> {
                        popWindowClickListener?.onCopy()
                        dismiss()
                    }
                    TYPE_REPLY -> {
                        popWindowClickListener?.onReply()
                        dismiss()
                    }
                    TYPE_FORWARD -> {
                        popWindowClickListener?.onForward()
                        dismiss()
                    }
                    TYPE_UNSEND -> {
                        popWindowClickListener?.onRecall()
                        dismiss()
                    }
                    TYPE_DELETE -> {
                        popWindowClickListener?.onDelete()
                        dismiss()
                    }
                    TYPE_PIN -> {
                        popWindowClickListener?.onPin()
                        dismiss()
                    }
                    TYPE_SELECT -> {
                        popWindowClickListener?.onSelect()
                        dismiss()
                    }
                }
            }

        }
    }


    private fun createSwitchViews(context: Context): Pair<ImageView, ImageView> {
        val size = AppUtil.dp2Px(context.resources, 40)
        val p = ImageView(context)
        p.scaleType = ImageView.ScaleType.CENTER_INSIDE
        p.setImageResource(R.drawable.chats_conversation_pop_previous_icon)
        p.layoutParams = ViewGroup.LayoutParams(size, size)
        val n = ImageView(context)
        n.scaleType = ImageView.ScaleType.CENTER_INSIDE
        n.setImageResource(R.drawable.chats_conversation_pop_next_icon)
        n.layoutParams = ViewGroup.LayoutParams(size, size)
        return Pair(p, n)
    }

    private fun initFloatPop(context: Context, anchorView: View, contentView: View) {
        popupWindow = PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        popupWindow.contentView = contentView
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true
        popupWindow.isTouchable = true
        popupWindow.setBackgroundDrawable(BitmapDrawable())
        popupWindow.animationStyle = R.style.ChatsConversationPopupStyle


        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val selfW = contentView.measuredWidth
        val selfH = contentView.measuredHeight

        val barHeight = AppUtil.getStatusBarHeight(context) + context.resources.getDimensionPixelSize(R.dimen.common_bcm_app_title_bar_height)
        val hg = context.resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
        val vg = 5
        val anchorLoc = IntArray(2)
        anchorView.getLocationOnScreen(anchorLoc)
        var left: Boolean? = null
        var top: Boolean = true

        var gravity = Gravity.NO_GRAVITY
        var tx = anchorLoc[0] + anchorView.width / 2 - selfW / 2
        if (tx <= hg) {
            tx = anchorLoc[0]
            if(isOutgoing) {
                gravity = Gravity.END or Gravity.TOP
            }else {
                gravity = Gravity.START or Gravity.TOP
            }
            left = true

        } else if (tx + selfW >= AppUtil.getScreenWidth(context)) {
            if(isOutgoing) {
                tx = AppUtil.getScreenWidth(context) - anchorLoc[0] - anchorView.width
                gravity = Gravity.END or Gravity.TOP
            }else {
                tx = anchorLoc[0] + anchorView.width - selfW
                gravity = Gravity.START or Gravity.TOP
            }
            left = false
        }

        var ty = anchorLoc[1] - selfH - vg
        if (ty <= barHeight) {
            ty = anchorLoc[1] + anchorView.height + vg
            top = false
        } else {
            top = true
        }

        if (top) {
            when (left) {
                true -> contentView.setBackgroundResource(R.drawable.chats_conversation_pop_top_left_bg)
                false -> contentView.setBackgroundResource(R.drawable.chats_conversation_pop_top_right_bg)
                else -> contentView.setBackgroundResource(R.drawable.chats_conversation_pop_top_bg)
            }
        } else {
            when (left) {
                true -> contentView.setBackgroundResource(R.drawable.chats_conversation_pop_down_left_bg)
                false -> contentView.setBackgroundResource(R.drawable.chats_conversation_pop_down_right_bg)
                else -> contentView.setBackgroundResource(R.drawable.chats_conversation_pop_down_bg)
            }
        }
        popupWindow.setOnDismissListener {
            popWindowClickListener?.onDismiss()
        }
        popupWindow.showAtLocation(anchorView, gravity, tx, ty)
    }

    private fun dismiss() {
        popupWindow.dismiss()
        setCurrentPop(null)
    }


    class ItemPopWindowBuilder(private val context: Context) {

        companion object {
            const val TYPE_COPY = 1
            const val TYPE_FORWARD = 2
            const val TYPE_PIN = 3
            const val TYPE_REPLY = 4
            const val TYPE_UNSEND = 5
            const val TYPE_DELETE = 6
            const val TYPE_SELECT = 7
        }

        private var anchorView: View? = null
        private var clickListener: PopWindowClickListener? = null
        private var isOutgoing = false
        private var canMultiSelect = true
        private var mDeletable = true
        private val itemList: MutableList<TextView> = mutableListOf()

        fun withAnchorView(anchorView: View): ItemPopWindowBuilder {
            this.anchorView = anchorView
            return this
        }

        fun withClickListener(clickListener: PopWindowClickListener): ItemPopWindowBuilder {
            this.clickListener = clickListener
            return this
        }

        fun withCopyVisible(copyVisible: Boolean): ItemPopWindowBuilder {
            if(copyVisible) {

                itemList.add(createItem(context, TYPE_COPY))

            }
            return this
        }

        fun withRecallVisible(recallVisible: Boolean): ItemPopWindowBuilder {
            if(recallVisible) {

                    itemList.add(createItem(context, TYPE_UNSEND))

            }
            return this
        }

        fun withPinVisible(pinVisible: Boolean, hasPin: Boolean): ItemPopWindowBuilder {
            if(pinVisible) {
                val pinItem = createItem(context, TYPE_PIN)
                if (hasPin) {
                    pinItem.text = context.getString(R.string.chats_unpin)
                }else {
                    pinItem.text = context.getString(R.string.chats_pin)
                }
                itemList.add(pinItem)

            }
            return this
        }

        fun withForwardable(forwardable: Boolean): ItemPopWindowBuilder {
            if(forwardable) {
                itemList.add(createItem(context, TYPE_FORWARD))
            }
            return this
        }

        fun withReplyable(replyable: Boolean): ItemPopWindowBuilder {
            if(replyable) {
                itemList.add(createItem(context, TYPE_REPLY))
            }
            return this
        }

        fun withOutgoing(isOutgoing: Boolean): ItemPopWindowBuilder {
            this.isOutgoing = isOutgoing
            return this
        }

        fun withMultiSelect(multiSelect: Boolean): ItemPopWindowBuilder {
            this.canMultiSelect = multiSelect
            return this
        }

        fun withDeletable(deletable: Boolean): ItemPopWindowBuilder {
            this.mDeletable = deletable
            return this
        }

        fun build(): ConversationItemPopWindow {
            if (mDeletable) {
                itemList.add(createItem(context, TYPE_DELETE))
            }
            if (canMultiSelect) {
                itemList.add(createItem(context, TYPE_SELECT))
            }
            return ConversationItemPopWindow(context,
                    anchorView
                            ?: throw Exception("anchorView is null"), isOutgoing, clickListener, sort(itemList))
        }

        private fun sort(itemList: List<TextView>): List<TextView> {
            return itemList.sortedWith(Comparator<TextView> { o1, o2 ->
                val p = o1.tag as Int
                val n = o2.tag as Int
                p - n
            })
        }

        private fun createItem(context: Context, type: Int): TextView {
            val tv = TextView(context)
            tv.gravity = Gravity.CENTER
            tv.setTextColor(AppUtil.getColor(context.resources, R.color.common_color_white))
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            tv.compoundDrawablePadding = AppUtil.dp2Px(context.resources, 2)

            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)

            val attribute = intArrayOf(android.R.attr.selectableItemBackground)
            val typedArray = context.theme.obtainStyledAttributes(typedValue.resourceId, attribute)

            tv.background = typedArray.getDrawable(0)
            tv.tag = type
            val layoutParams = LinearLayout.LayoutParams(AppUtil.dp2Px(context.resources, 50), LinearLayout.LayoutParams.WRAP_CONTENT)
            tv.layoutParams = layoutParams

            when(type) {
                TYPE_COPY -> {
                    val d = AppUtil.getDrawable(context.resources, R.drawable.chats_conversation_pop_copy_icon)
                    d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                    tv.setCompoundDrawables(null, d, null, null)
                    tv.text = context.getString(R.string.chats_copy)
                }
                TYPE_REPLY -> {
                    val d = AppUtil.getDrawable(context.resources, R.drawable.chats_conversation_pop_reply_icon)
                    d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                    tv.setCompoundDrawables(null, d, null, null)
                    tv.text = context.getString(R.string.chats_reply)
                }
                TYPE_FORWARD -> {
                    val d = AppUtil.getDrawable(context.resources, R.drawable.chats_conversation_pop_forward_icon)
                    d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                    tv.setCompoundDrawables(null, d, null, null)
                    tv.text = context.getString(R.string.chats_forward)
                }
                TYPE_UNSEND -> {
                    val d = AppUtil.getDrawable(context.resources, R.drawable.chats_conversation_pop_recall_icon)
                    d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                    tv.setCompoundDrawables(null, d, null, null)
                    tv.text = context.getString(R.string.chats_text_recall)
                }
                TYPE_DELETE -> {
                    val d = AppUtil.getDrawable(context.resources, R.drawable.chats_conversation_pop_delete_icon)
                    d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                    tv.setCompoundDrawables(null, d, null, null)
                    tv.text = context.getString(R.string.chats_delete)
                }
                TYPE_PIN -> {
                    val d = AppUtil.getDrawable(context.resources, R.drawable.chats_conversation_pop_pin_icon)
                    d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                    tv.setCompoundDrawables(null, d, null, null)
                    tv.text = context.getString(R.string.chats_pin)
                }
                TYPE_SELECT -> {
                    val d = AppUtil.getDrawable(context.resources, R.drawable.chats_conversation_pop_select_icon)
                    d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                    tv.setCompoundDrawables(null, d, null, null)
                    tv.text = context.getString(R.string.chats_select)
                }
            }
            return tv
        }
    }

    interface PopWindowClickListener {
        fun onRecall()
        fun onDelete()
        fun onCopy()
        fun onForward()
        fun onSelect()
        fun onPin()
        fun onReply()
        fun onDismiss(){}
    }
}