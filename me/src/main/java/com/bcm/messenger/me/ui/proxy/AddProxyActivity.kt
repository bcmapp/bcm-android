package com.bcm.messenger.me.ui.proxy

import android.os.Bundle
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.netswitchy.proxy.ProxyItem
import com.bcm.netswitchy.proxy.ProxyManager
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParamsParser
import kotlinx.android.synthetic.main.me_activity_add_proxy.*

class AddProxyActivity : SwipeBaseActivity() {
    companion object {
        const val EDIT_PROXY_NAME = "edit_proxy_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_add_proxy)

        add_proxy_title_bar.setCenterText(getString(R.string.me_add_proxy_title))
        add_proxy_title_bar.setRightText(getString(R.string.me_setting_proxy_action_add))
        val editName = intent?.getStringExtra(EDIT_PROXY_NAME)
        if (editName?.isNotEmpty() == true) {
            val proxyItem = ProxyManager.getProxyByName(editName)
            if (null != proxyItem) {
                add_proxy_name_edit.setText(editName)
                add_proxy_content_edit.setText(proxyItem.content)
                add_proxy_title_bar.setCenterText(getString(R.string.me_add_proxy_edit_title))
                add_proxy_title_bar.setRightText(getString(R.string.me_add_proxy_action_save))
            }
        }

        add_proxy_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                if (QuickOpCheck.getDefault().isQuick) {
                    return
                }

                val name = add_proxy_name_edit.text.toString().trim()
                val content = add_proxy_content_edit.text.toString().trim()

                if (content.isEmpty()) {
                    return
                }

                val params = ProxyParamsParser.parse(content)
                if (params == null) {
                    AmeAppLifecycle.failure(getString(R.string.me_proxy_add_error_format), true)
                    return
                }

                params.name = name

                ProxyManager.addProxy(ProxyItem(params, params.toString(), ProxyItem.Status.UNKNOWN), false)
                finish()
            }
        })

    }
}