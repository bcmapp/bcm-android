package com.bcm.messenger.me.ui.proxy

import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.adapter.ListDataSource
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.netswitchy.proxy.IProxyStateChanged
import com.bcm.netswitchy.proxy.ProxyItem
import com.bcm.netswitchy.proxy.ProxyManager
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.me_activity_proxy.*
import java.lang.StringBuilder

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

        ProxyManager.refresh()

        proxy_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
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

        proxy_setting_test.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            if (!ProxyManager.isTesting()) {
                ProxyManager.startTester()
            }
        }


        proxy_server_list.layoutManager = LinearLayoutManager(this)
        val adapter = AmeRecycleViewAdapter(this, dataSource)
        adapter.setViewHolderDelegate(this)
        adapter.setHasStableIds(true)
        proxy_server_list.adapter = adapter

        dataSource.updateDataSource(ProxyManager.getProxyList())

        if (!AppUtil.isReleaseBuild()) {
            proxy_test_run.visibility = View.VISIBLE
            proxy_test_run.setSwitchEnable(false)

            proxy_test_run.setSwitchStatus(ProxyManager.isProxyRunning())
            proxy_test_run.setOnClickListener {
                if (!ProxyManager.isProxyRunning()) {
                    ProxyManager.startProxy()
                } else {
                    ProxyManager.stopProxy()
                }
                updateDevState()
            }
            updateDevState()
        }
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
        if (!AppUtil.isReleaseBuild()) {
            proxy_running_state.visibility = View.VISIBLE

            val stateBuilder = StringBuilder()

            val runningProxy = ProxyManager.getRunningProxyTitle()
            if (runningProxy.isNotEmpty()) {
                stateBuilder.append("running proxy:$runningProxy\n")
            }

            val testingProxy = ProxyManager.getTestingProxyName()
            if (testingProxy.isNotEmpty()) {
                stateBuilder.append("testing proxy:$testingProxy\n")
            }

            if (testingProxy.isEmpty() && runningProxy.isEmpty()) {
                stateBuilder.append("no state")
            }

            proxy_running_state.text = stateBuilder.toString()

            proxy_test_run.setSwitchStatus(ProxyManager.isProxyRunning())
        }
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<ProxyItem>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<ProxyItem> {
        return ViewHolder(LayoutInflater.from(this).inflate(R.layout.me_proxy_item, parent, false))
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<ProxyItem>, viewHolder: AmeRecycleViewAdapter.ViewHolder<ProxyItem>) {
        if (QuickOpCheck.getDefault().isQuick) {
            return
        }

        val data = viewHolder.getData()?.params ?: return
        startActivity(Intent(this@ProxySettingActivity, AddProxyActivity::class.java).apply {
            putExtra(AddProxyActivity.EDIT_PROXY_NAME, data.name)
        })
    }

    override fun onViewLongClicked(adapter: AmeRecycleViewAdapter<ProxyItem>, viewHolder: AmeRecycleViewAdapter.ViewHolder<ProxyItem>): Boolean {
        val data = viewHolder.getData()?.params ?: return false

        val menuItems = listOf(
                BcmPopupMenu.MenuItem(getString(R.string.me_note_item_delete))
        )

        BcmPopupMenu.Builder(this)
                .setMenuItem(menuItems)
                .setAnchorView(viewHolder.itemView)
                .setSelectedCallback { index ->
                    when (index) {
                        0 -> {
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

    private class ViewHolder(view: View) : AmeRecycleViewAdapter.ViewHolder<ProxyItem>(view) {
        private val name = view.findViewById<TextView>(R.id.proxy_name)
        private val status = view.findViewById<TextView>(R.id.proxy_status)
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
            val sep = if (data.status == ProxyItem.Status.UNKNOWN) {
                ""
            } else {
                "・"
            }
            val t = "${data.params.name}${sep}"
            name.text = t

            when (data.status) {
                ProxyItem.Status.USABLE -> {
                    status.text = name.context.getString(R.string.me_setting_proxy_item_usable)
                    status.setTextColor(getColor(R.color.common_3ED645))
                    status.visibility = View.VISIBLE
                }
                ProxyItem.Status.UNUSABLE -> {
                    status.text = name.context.getString(R.string.me_setting_proxy_item_unusable)
                    status.setTextColor(getColor(R.color.common_red))
                    status.visibility = View.VISIBLE
                }
                ProxyItem.Status.TESTING -> {
                    status.text = name.context.getString(R.string.me_setting_proxy_item_testing)
                    status.setTextColor(getColor(R.color.common_red))
                    status.visibility = View.VISIBLE
                }
                else -> {
                    status.visibility = View.GONE
                }
            }

            content.text = data.content
        }
    }
}