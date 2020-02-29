package com.bcm.messenger.me.ui.accesspoint

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
import com.bcm.messenger.common.bcmhttp.imserver.AccessPointConfigure
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.adapter.ListDataSource
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.me_activity_proxy.*

@Route(routePath = ARouterConstants.Activity.ACCESS_POINT_SETTING)
class AccessPointSettingActivity : SwipeBaseActivity()
        , AmeRecycleViewAdapter.IViewHolderDelegate<String> {
    private val dataSource = object : ListDataSource<String>() {
        override fun getItemId(position: Int): Long {
            return EncryptUtils.byteArrayToLong(getData(position).toByteArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_proxy)

        function_title_bar.setCenterText(getString(R.string.me_setting_access_point_title))
        function_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                if (QuickOpCheck.getDefault().isQuick) {
                    return
                }
                startActivity(Intent(this@AccessPointSettingActivity, AddAccessPointActivity::class.java))
            }
        })

        server_list.layoutManager = LinearLayoutManager(this)
        val adapter = AmeRecycleViewAdapter(this, dataSource)
        adapter.setViewHolderDelegate(this)
        adapter.setHasStableIds(true)
        server_list.adapter = adapter

        dataSource.updateDataSource(AccessPointConfigure.list)

        select_title.text = getString(R.string.me_access_point_configurations)
        function_enable.setName(getString(R.string.me_enable_access_point))
        function_enable.setSwitchStatus(AccessPointConfigure.isEnable)
        function_enable.setSwitchEnable(false)
        function_enable.setOnClickListener {
            if (AccessPointConfigure.list.isEmpty()) {
                ToastUtil.show(this, getString(R.string.me_no_access_point_found))
                return@setOnClickListener
            }

            AmePopup.center.newBuilder()
                    .withTitle(getString(R.string.common_alert_tip))
                    .withContent(getString(R.string.me_change_access_point_tip))
                    .withOkTitle(getString(R.string.common_popup_ok))
                    .withCancelTitle(getString(R.string.common_cancel))
                    .withOkListener {
                        AccessPointConfigure.isEnable = !AccessPointConfigure.isEnable
                        if (AccessPointConfigure.isEnable && AccessPointConfigure.current.isNotEmpty()) {
                            AccessPointConfigure.current = AccessPointConfigure.list.first()
                            server_list.adapter?.notifyDataSetChanged()
                        }
                        updateDevState()
                    }.show(this)
        }
        updateDevState()
    }

    override fun onResume() {
        super.onResume()
        dataSource.updateDataSource(AccessPointConfigure.list)
    }

    private fun updateDevState() {
        function_enable.setSwitchStatus(AccessPointConfigure.isEnable)
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<String>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<String> {
        return ViewHolder(LayoutInflater.from(this).inflate(R.layout.me_proxy_item, parent, false))
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<String>, viewHolder: AmeRecycleViewAdapter.ViewHolder<String>) {
        if (QuickOpCheck.getDefault().isQuick) {
            return
        }

        val data = viewHolder.getData() ?: return

        if (!AccessPointConfigure.isEnable) {
            AccessPointConfigure.current = data
            server_list.adapter?.notifyDataSetChanged()
        } else if (AccessPointConfigure.current != data) {
            AmePopup.center.newBuilder()
                    .withTitle(getString(R.string.common_alert_tip))
                    .withContent(getString(R.string.me_change_access_point_tip))
                    .withOkTitle(getString(R.string.common_popup_ok))
                    .withCancelTitle(getString(R.string.common_cancel))
                    .withOkListener {
                        AccessPointConfigure.current = data
                        server_list.adapter?.notifyDataSetChanged()
                    }.show(this)

        }
    }

    override fun onViewLongClicked(adapter: AmeRecycleViewAdapter<String>, viewHolder: AmeRecycleViewAdapter.ViewHolder<String>): Boolean {
        val data = viewHolder.getData() ?: return false

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
                            startActivity(Intent(this@AccessPointSettingActivity, AddAccessPointActivity::class.java).apply {
                                putExtra(AddAccessPointActivity.ACCESS_POINT_NAME, data)
                            })
                        }
                        1 -> {
                            AmePopup.bottom.newBuilder()
                                    .withTitle(getString(R.string.me_setting_proxy_pop_delete, data))
                                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_note_item_delete), AmeBottomPopup.PopupItem.CLR_RED) {
                                        AccessPointConfigure.remotePoint(data)
                                        dataSource.updateDataSource(AccessPointConfigure.list)
                                    })
                                    .withDoneTitle(getString(R.string.common_cancel))
                                    .show(this)
                        }
                    }
                }
                .show((viewHolder as ViewHolder).lastTouchPoint.x, viewHolder.lastTouchPoint.y)
        return true
    }

    private inner class ViewHolder(view: View) : AmeRecycleViewAdapter.ViewHolder<String>(view) {
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

            content.visibility = View.GONE
        }

        override fun setData(data: String) {
            super.setData(data)
            name.text = data

            if (data == AccessPointConfigure.current) {
                check.visibility = View.VISIBLE
            } else {
                check.visibility = View.GONE
            }
        }
    }
}