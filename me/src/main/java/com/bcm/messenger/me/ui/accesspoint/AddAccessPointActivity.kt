package com.bcm.messenger.me.ui.accesspoint

import android.os.Bundle
import android.view.View
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.bcmhttp.imserver.AccessPointConfigure
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.QuickOpCheck
import kotlinx.android.synthetic.main.me_activity_add_proxy.*
import java.net.URI

class AddAccessPointActivity : SwipeBaseActivity() {
    companion object {
        const val ACCESS_POINT_NAME = "access_point_name"
    }

    private var orgPoint = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_add_proxy)

        add_proxy_name_edit.visibility = View.GONE
        proxy_t1.visibility = View.GONE
        proxy_t2.visibility = View.GONE

        format_describe.text = "eg: https://127.0.0.1:6080"

        add_proxy_title_bar.setCenterText(getString(R.string.me_setting_access_point_title))
        add_proxy_title_bar.setRightText(getString(R.string.me_setting_proxy_action_add))
        val point = intent?.getStringExtra(ACCESS_POINT_NAME)
        if (point?.isNotEmpty() == true) {
            orgPoint = point

            add_proxy_title_bar.setCenterText("${getString(R.string.common_edit)}${getString(R.string.me_setting_access_point_title)}")
            add_proxy_content_edit.setText(point)
            add_proxy_title_bar.setRightText(getString(R.string.me_add_proxy_action_save))
        }

        add_proxy_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                if (QuickOpCheck.getDefault().isQuick) {
                    return
                }

                val content = getHost(add_proxy_content_edit.text.toString().trim()) ?: return

                if (content.isEmpty()) {
                    ToastUtil.show(this@AddAccessPointActivity, "Bad host format")
                    return
                }

                if (orgPoint.isNotEmpty()) {
                    AccessPointConfigure.remotePoint(orgPoint)
                }
                AccessPointConfigure.addPoint(content)

                finish()
            }
        })
    }

    private fun getHost(host: String): String? {
        if (host.isEmpty()) {
            return null
        }

        val url = if (!host.startsWith("http", true)) {
            "http://$host"
        } else {
            host
        }

        try {
            val uri = URI(url)
            if ((uri.scheme?.isNotEmpty() == true
                            && uri.host?.isNotEmpty()==true) && uri.path.isNullOrEmpty()) {
                val port = if (uri.port > 0) {
                    uri.port
                } else {
                    1080
                }
                return "${uri.scheme}://${uri.host}:$port"
            }
        } catch (e:Throwable) {
        }
        return ""
    }
}