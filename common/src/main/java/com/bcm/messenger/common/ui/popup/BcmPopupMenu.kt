package com.bcm.messenger.common.ui.popup

import android.annotation.SuppressLint
import android.content.Context
import android.view.*
import androidx.annotation.DrawableRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import java.lang.ref.WeakReference

/**
 * 可以在指定位置弹出的PopupMenu，基于PopupWindow
 *
 * Created by Kin on 2019/7/2
 */
class BcmPopupMenu {
    companion object {
        private val inst = BcmPopupMenu()
        fun getInstance() = inst
    }

    @SuppressLint("RestrictedApi")
    private fun showMenu(config: MenuConfig) {
        val context = config.context?.get() ?: return
        val anchorView = config.anchorView ?: return

        val popupMenu = PopupMenu(context, anchorView)
        val menu = popupMenu.menu
        config.menuItem.forEachIndexed { index, menuText ->
            menu.add(Menu.NONE, index, index, menuText.title)
            menu.getItem(index).setIcon(menuText.iconRes)
            (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            config.selectCallback(menuItem.itemId)
            return@setOnMenuItemClickListener true
        }
        popupMenu.setOnDismissListener {
            config.dismissCallback()
        }

        try {
            val field = popupMenu.javaClass.getDeclaredField("mPopup")
            field.isAccessible = true
            val helper = field.get(popupMenu) as MenuPopupHelper
            if (config.x == -1 || config.y == -1) {
                helper.show()
            } else {
                helper.show(config.x, config.y)
            }
            helper.setForceShowIcon(true)
        } catch (e: Exception) {
            popupMenu.show()
        }
    }

    class Builder(context: Context) {
        private val config = MenuConfig()

        init {
            config.context = WeakReference(context)
        }

        fun setMenuItem(items: List<MenuItem>): Builder {
            config.menuItem = items
            return this
        }

        fun setSelectedCallback(callback: (Int) -> Unit): Builder {
            config.selectCallback = callback
            return this
        }

        fun setAnchorView(view: View): Builder {
            config.anchorView = view
            return this
        }

        fun setGravity(gravity: Int): Builder {
            config.gravity = gravity
            return this
        }

        fun setDismissCallback(callback: () -> Unit): Builder {
            config.dismissCallback = callback
            return this
        }

        /**
         * 在给定的x y偏移量弹出菜单
         * x y一般是rawX和y - view.height
         */
        fun show(x: Int, y: Int) {
            config.x = x
            config.y = y
            getInstance().showMenu(config)
        }

        /**
         * 按照PopupMenu的默认方式弹出菜单
         */
        fun show() {
            getInstance().showMenu(config)
        }
    }

    class MenuConfig {
        var context: WeakReference<Context>? = null
        var anchorView: View? = null
        var menuItem = listOf<MenuItem>()
        var selectCallback: (Int) -> Unit = {}
        var dismissCallback: () -> Unit = {}
        var gravity = Gravity.NO_GRAVITY
        var x = -1
        var y = -1
    }

    class MenuItem(val title: String, @DrawableRes val iconRes: Int = 0)
}