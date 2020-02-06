package com.bcm.messenger.chats.components.titlebar

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.QuickOpCheck

/**
 * bcm.social.01 2019/1/27.
 */
class ChatTitleDropBar:LinearLayout {
    companion object {
        //item id
        const val DropItem_Security = 1
        const val DropItem_Mining = 2
        const val DropItem_AutoClear = 3
        const val DropItem_Drop = 0xFFFFFF
    }
    private var itemList = mutableListOf<Item>()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        addItem(DropItem_Drop, R.drawable.chats_8_down)
        setOnClickListener {
            showDropMenu()
        }
    }

    inner class Item(val itemId:Int, var icon:Int = 0, var dropItem: ChatTitleDropItem? = null, var iconView:ImageView)

    fun addItem(itemId:Int, icon:Int, dropItem: ChatTitleDropItem? = null) {
        assert(itemId != 0 && icon != 0)

        val found = findIem(itemId)
        if (found != null){
            assert(false){
                "Status is same"
            }
            return
        }

        val iconView = ImageView(context)
        iconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        iconView.setImageResource(icon)
        itemList.add(Item(itemId, icon, dropItem, iconView))

        itemList = itemList.sortedBy {
            it.itemId
        }.toMutableList()

        removeAllViewsInLayout()

        for (i in itemList) {
            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            params.setMargins(AppUtil.dp2Px(context.resources, 2), 0, AppUtil.dp2Px(context.resources, 2), AppUtil.dp2Px(context.resources, 3))
            i.iconView.layoutParams = params
            addView(i.iconView)
        }
    }

    fun replaceItem(itemId: Int, icon: Int, item: ChatTitleDropItem? = null) {
        val found = findIem(itemId)
        if (null == found){
            addItem(itemId, icon, item)
        } else {
            found.icon = icon
            found.dropItem = item
            found.iconView.setImageResource(icon)
        }
    }

    fun removeItem(itemId:Int){
        val found = findIem(itemId)
        if (null != found){
            removeView(found.iconView)
            itemList.remove(found)
        }
    }

    private fun findIem(itemId:Int): Item? {
        for (i in itemList){
            if (i.itemId == itemId){
                return i
            }
        }
        return null
    }

    fun showDropMenu() {
        if (QuickOpCheck.getDefault().isQuick){
            return
        }

        val dropItem = findIem(DropItem_Drop)?:return
        if(dropItem.icon == R.drawable.chats_8_down){
            val actionList:ArrayList<ChatTitleDropItem> = ArrayList()
            itemList.forEach {
                val item = it.dropItem
                if (item != null){
                    actionList.add(item)
                }
            }

            if (actionList.isNotEmpty()){
                val dropMenu = ChatTitleDropMenu()
                dropMenu.setOnDismissListener {
                    replaceItem(DropItem_Drop, R.drawable.chats_8_down)
                }
                dropMenu.updateMenu(actionList)
                dropMenu.showAsDropDown(this)
            }

            replaceItem(DropItem_Drop, R.drawable.chats_8_up)
        } else {
            replaceItem(DropItem_Drop, R.drawable.chats_8_down)
        }
    }
}