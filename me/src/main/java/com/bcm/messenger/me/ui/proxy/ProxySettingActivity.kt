package com.bcm.messenger.me.ui.proxy

import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.adapter.ListDataSource
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.me.R
import com.bcm.netswitchy.proxy.ProxyConfigure
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.netswitchy.proxy.IProxyStateChanged
import com.bcm.netswitchy.proxy.ProxyItem
import com.bcm.netswitchy.proxy.ProxyManager
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.me_activity_proxy.*

@Route(routePath = ARouterConstants.Activity.PROXY_SETTING)
class ProxySettingActivity : SwipeBaseActivity()
        , AmeRecycleViewAdapter.IViewHolderDelegate<ProxyItem>
        , IProxyStateChanged {
    private val dataSource = object : ListDataSource<ProxyItem>() {
        override fun getItemId(position: Int): Long {
            return EncryptUtils.byteArrayToLong(getData(position).params.name.toByteArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_proxy)

        ProxyManager.setListener(this)

//        ProxyManager.refresh()

        function_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                if (QuickOpCheck.getDefault().isQuick) {
                    return
                }
                startActivity(Intent(this@ProxySettingActivity, AddProxyActivity::class.java))
            }
        })

        server_list.layoutManager = LinearLayoutManager(this)
        val adapter = AmeRecycleViewAdapter(this, dataSource)
        adapter.setViewHolderDelegate(this)
        adapter.setHasStableIds(true)
        server_list.adapter = adapter

        dataSource.updateDataSource(ProxyManager.getProxyList())

        function_enable.setSwitchStatus(ProxyConfigure.isEnable)
        function_enable.setSwitchEnable(false)
        function_enable.setOnClickListener {
            if (!ProxyConfigure.isEnable && ProxyManager.getProxyList().isEmpty()) {
                ToastUtil.show(this, getString(R.string.me_no_proxy_found))
            }
            ProxyConfigure.isEnable = !ProxyConfigure.isEnable
            ProxyConfigure.checkProxy()
            if (ProxyConfigure.isEnable) {
                dataSource.refresh()
            }
            updateDevState()
        }
        updateDevState()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ProxyManager.isTesting()) {
            ProxyManager.stopTester()
        }
    }

    override fun onProxyListChanged() {
        dataSource.updateDataSource(ProxyManager.getProxyList())

        if (!AppUtil.isReleaseBuild()) {
            updateDevState()
        }
    }

    override fun onProxyConnectStarted() {
        if (!AppUtil.isReleaseBuild()) {
            updateDevState()
        }
    }

    override fun onProxyConnectFinished() {
        if (!AppUtil.isReleaseBuild()) {
            updateDevState()
        }
    }

    private fun updateDevState() {
        function_enable.setSwitchStatus(ProxyConfigure.isEnable)
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<ProxyItem>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<ProxyItem> {
        return ViewHolder(LayoutInflater.from(this).inflate(R.layout.me_proxy_item, parent, false))
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<ProxyItem>, viewHolder: AmeRecycleViewAdapter.ViewHolder<ProxyItem>) {
        if (QuickOpCheck.getDefault().isQuick) {
            return
        }

        val data = viewHolder.getData()?.params ?: return
        if (ProxyConfigure.current != data.name) {
            ProxyConfigure.current = data.name
            ProxyConfigure.checkProxy()
            server_list.adapter?.notifyDataSetChanged()
        }
    }

    override fun onViewLongClicked(adapter: AmeRecycleViewAdapter<ProxyItem>, viewHolder: AmeRecycleViewAdapter.ViewHolder<ProxyItem>): Boolean {
        val data = viewHolder.getData()?.params ?: return false

        val menuItems = listOf(
                BcmPopupMenu.MenuItem(getString(R.string.common_edit)),
                BcmPopupMenu.MenuItem(getString(R.string.me_note_item_delete))
        )

        BcmPopupMenu.Builder(this)
                .setMenuItem(menuItems)
                .setAnchorView(viewHolder.itemView)
                .setSelectedCallback { index ->
                    when (index) {
                        0 -> {
                            startActivity(Intent(this@ProxySettingActivity, AddProxyActivity::class.java).apply {
                                putExtra(AddProxyActivity.EDIT_PROXY_NAME, data.name)
                            })
                        }
                        1 -> {
                            AmePopup.bottom.newBuilder()
                                    .withTitle(getString(R.string.me_setting_proxy_pop_delete, data.name))
                                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_note_item_delete), AmeBottomPopup.PopupItem.CLR_RED) {
                                        ProxyManager.removeProxy(data.name)
                                    })
                                    .withDoneTitle(getString(R.string.common_cancel))
                                    .show(this)
                        }
                    }
                }
                .show((viewHolder as ViewHolder).lastTouchPoint.x, viewHolder.lastTouchPoint.y)
        return true
    }

    private inner class ViewHolder(view: View) : AmeRecycleViewAdapter.ViewHolder<ProxyItem>(view) {
        private val name = view.findViewById<TextView>(R.id.proxy_name)
        private val check = view.findViewById<ImageView>(R.id.proxy_select)
        private val content = view.findViewById<TextView>(R.id.proxy_content)
        val lastTouchPoint = Point()

        init {
            view.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    lastTouchPoint.set(event.rawX.toInt(), event.y.toInt() - view.height)
                }
                return@setOnTouchListener false
            }
        }

        override fun setData(data: ProxyItem) {
            super.setData(data)
            name.text = data.params.name

            if (data.params.name == ProxyConfigure.current) {
                check.visibility = View.VISIBLE
            } else {
                check.visibility = View.GONE
            }

            content.text = data.content
        }
    }
}