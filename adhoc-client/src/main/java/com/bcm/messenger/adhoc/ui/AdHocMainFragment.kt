package com.bcm.messenger.adhoc.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.component.AdHocSessionAvatar
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.logic.AdHocSession
import com.bcm.messenger.adhoc.logic.AdHocSessionLogic
import com.bcm.messenger.adhoc.ui.channel.AdHocConversationActivity
import com.bcm.messenger.adhoc.ui.channel.AdHocJoinChannelActivity
import com.bcm.messenger.adhoc.ui.channel.CurrentSearchFragment
import com.bcm.messenger.adhoc.ui.channel.RecentSearchFragment
import com.bcm.messenger.adhoc.ui.setting.AdHocDevSettingActivity
import com.bcm.messenger.adhoc.ui.setting.AdHocSettingActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.event.HomeTabEvent
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.theme.ThemeManager
import com.bcm.messenger.common.ui.CommonSearchBar
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.activity.SearchActivity
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.adapter.ListDataSource
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.*
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.adhoc_channel_main_fragment.*
import java.lang.ref.WeakReference


@Route(routePath = ARouterConstants.Activity.APP_HOME_AD_HOC_MAIN)
class AdHocMainFragment: BaseFragment(),
        AmeRecycleViewAdapter.IViewHolderDelegate<AdHocSession>,
        AdHocSessionLogic.IAdHocSessionListener,
        AdHocChannelLogic.IAdHocChannelListener,
        AdHocDeviceStateListener.IDeviceStateListener {

    companion object {
        private const val TAG = "AdHocMainFragment"

        private const val FIND_SESSION_REQUEST = 100
        private const val SEARCHBAR_TYPE = 0
        private const val SESSION_TYPE = 1
    }

    private lateinit var deviceStateListener:AdHocDeviceStateListener
    private lateinit var adHocStep:AdHocConnectingStep

    private lateinit var adHocRequire: AdHocDeviceRequire
    private var disposeRefresh:Disposable? = null

    private val dataSource = object :ListDataSource<AdHocSession>() {
        override fun getItemId(position: Int): Long {
            if (position == 0) {
                return -1L
            }
            return EncryptUtils.byteArrayToLong(getData(position).sessionId.toByteArray())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FIND_SESSION_REQUEST && resultCode == Activity.RESULT_OK) {
            val targetSession = data?.getStringExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION)
            if (targetSession != null) {
                startBcmActivity(Intent(context, AdHocConversationActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, targetSession)
                })
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.adhoc_channel_main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceStateListener = AdHocDeviceStateListener(accountContext)
        adHocStep = AdHocConnectingStep(accountContext)

        adHocRequire = AdHocDeviceRequire(accountContext, view.context as Activity)
        adHocRequire.require()

        adhoc_main_toolbar.setListener(object : CommonTitleBar2.TitleBarClickListener(){
            override fun onClickRight() {
                AmePopup.bottom.newBuilder()
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.adhoc_create_chat_room)){
                            val intent = Intent(context, AdHocJoinChannelActivity::class.java)
                            intent.putExtra("name", getString(R.string.adhoc_create_chat_room))
                            intent.putExtra("option", getString(R.string.adhoc_chat_room_create))
                            startBcmActivity(intent)
                        })
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.adhoc_join_chat_room)) {
                            val intent = Intent(context, AdHocJoinChannelActivity::class.java)
                            intent.putExtra("name", getString(R.string.adhoc_join_chat_room))
                            intent.putExtra("option", getString(R.string.adhoc_chat_room_join))
                            startBcmActivity(intent)
                        })
                        .withDoneTitle(getString(R.string.common_cancel))
                        .show(this@AdHocMainFragment.context as? FragmentActivity)
            }
        })

        adhoc_main_toolbar.getCenterView().second?.setOnClickListener {
            ALog.i("AdHocMainFragment", "click disable")
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val intent = Intent(it.context, AdHocSettingActivity::class.java)
            startBcmActivity(intent)
        }

        adhoc_main_device_error.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            adHocRequire.checkRequirement()
        }

        val titleSwitcher = adhoc_main_toolbar.getCenterView().second?.findViewById<TextSwitcher>(R.id.adhoc_main_text_title)
        titleSwitcher?.setText(accountContext.name)

        if (!AppUtil.isReleaseBuild()) {
            val settingGuide = adhoc_main_toolbar.getCenterView().second?.findViewById<View>(R.id.adhoc_setting_guide)
            settingGuide?.setOnClickListener(MultiClickObserver(5, object : MultiClickObserver.MultiClickListener {
                override fun onMultiClick(view: View?, count: Int) {
                    startBcmActivity(Intent(context, AdHocDevSettingActivity::class.java))
                }
            }))
        }

        adhoc_main_channel_list.layoutManager = LinearLayoutManager(view.context)
        val adapter = AmeRecycleViewAdapter(view.context,dataSource)
        adapter.setViewHolderDelegate(this)
        adapter.setHasStableIds(true)
        adhoc_main_channel_list.adapter = adapter

        AdHocMessageLogic.get(accountContext)
        AdHocSessionLogic.get(accountContext).setListener(this)
        dataSource.updateDataSource(listOf(createSearchBarSessionData()) + AdHocSessionLogic.get(accountContext).getSessionList())
        RxBus.post(HomeTabEvent(accountContext, HomeTabEvent.TAB_ADHOC, false, AdHocSessionLogic.get(accountContext).getUnReadSessionCount()))

        deviceStateListener.setListener(this)
        deviceStateListener.init()
        AdHocChannelLogic.get(accountContext).setNotifyClass(activity?.javaClass)
        AdHocChannelLogic.get(accountContext).addListener(this)
        AdHocChannelLogic.get(accountContext).initAdHoc(accountContext)

        activity?.window?.setStatusBarLightMode()

        val wSelf = WeakReference(this)
        adHocStep.init {
            wSelf.get()?.updateAdHocState()
        }
        titleSwitcher?.setText(adHocStep.getStepDescribe())

        onScanStateChanged(AdHocChannelLogic.get(accountContext).getConnState())
    }

    private fun updateAdHocState() {
        val titleSwitcher = adhoc_main_toolbar.getCenterView().second?.findViewById<TextSwitcher>(R.id.adhoc_main_text_title)
        val text = adHocStep.getStepDescribe()
        val currentTitle = titleSwitcher?.currentView as? TextView
        if (text != currentTitle?.text.toString()) {
            titleSwitcher?.setText(text)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AdHocChannelLogic.get(accountContext).unInitAdHoc()
        deviceStateListener.unInit()
        adHocStep.unInit()
        AdHocChannelLogic.get(accountContext).removeListener(this)
        adHocRequire.unRequire()

        if (disposeRefresh?.isDisposed == false) {
            disposeRefresh?.dispose()
        }
    }

    override fun getViewHolderType(adapter: AmeRecycleViewAdapter<AdHocSession>, position: Int, data: AdHocSession): Int {
        return if (position == 0) {
            SEARCHBAR_TYPE
        }else {
            SESSION_TYPE
        }
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<AdHocSession>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<AdHocSession> {
        if (viewType == SEARCHBAR_TYPE) {
            val searchBar = CommonSearchBar(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                val lr = resources.getDimensionPixelSize(com.bcm.messenger.chats.R.dimen.common_horizontal_gap)
                val tb = resources.getDimensionPixelSize(com.bcm.messenger.chats.R.dimen.common_vertical_gap)
                setPadding(lr, tb, lr, 0)
                setMode(CommonSearchBar.MODE_DISPLAY)
                setOnSearchActionListener(object : CommonSearchBar.OnSearchActionListener {
                    override fun onJump() {
                        SearchActivity.callSearchActivity(context, accountContext,"", true, false, CurrentSearchFragment::class.java.name, RecentSearchFragment::class.java.name, 0)
                    }

                    override fun onSearch(keyword: String) {
                    }

                    override fun onClear() {
                    }

                })
            }
            return AmeRecycleViewAdapter.ViewHolder(searchBar)
        }
        else {
            val view = inflater.inflate(R.layout.adhoc_channel_item_layout, parent, false)
            return AdHocItemHolder(accountContext, view)
        }
    }

    override fun bindViewHolder(adapter: AmeRecycleViewAdapter<AdHocSession>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AdHocSession>) {
        super.bindViewHolder(adapter, viewHolder)
        if (viewHolder.itemView is CommonSearchBar) {
            var tp = resources.getDimensionPixelSize(com.bcm.messenger.chats.R.dimen.common_vertical_gap)
            if (deviceStateListener.getState().isNotEmpty()) {
                tp += 40.dp2Px()
            }

            val curTp = viewHolder.itemView.paddingTop
            if (curTp != tp) {
                val lr = resources.getDimensionPixelSize(com.bcm.messenger.chats.R.dimen.common_horizontal_gap)
                viewHolder.itemView.setPadding(lr, tp, lr, 0)
            }
        }
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<AdHocSession>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AdHocSession>) {
        super.onViewClicked(adapter, viewHolder)
        if (QuickOpCheck.getDefault().isQuick) {
            return
        }
        if (viewHolder is AdHocItemHolder) {
            val data = viewHolder.getData() as AdHocSession
            startBcmActivity(Intent(context, AdHocConversationActivity::class.java).apply {
                putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, data.sessionId)
            })
        }
    }

    override fun onViewLongClicked(adapter: AmeRecycleViewAdapter<AdHocSession>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AdHocSession>): Boolean {
        val data = viewHolder.getData() as AdHocSession

        val pinTitle = if(data.pin){
            getString(R.string.adhoc_session_item_unpin)
        } else {
            getString(R.string.adhoc_session_item_pin)
        }

        val menuItems = listOf(
                BcmPopupMenu.MenuItem(pinTitle),
                BcmPopupMenu.MenuItem(getString(R.string.common_delete))
        )

        val lastPoint = (viewHolder as AdHocItemHolder).lastTouchPoint
        BcmPopupMenu.Builder(context ?: return false)
                .setMenuItem(menuItems)
                .setAnchorView(viewHolder.itemView)
                .setSelectedCallback { index ->
                    when (index) {
                        0 -> switchNotePin(data.sessionId, !data.pin)
                        1 -> {
                            AlertDialog.Builder(context ?: return@setSelectedCallback)
                                    .setTitle(R.string.chats_item_confirm_delete_title)
                                    .setMessage(R.string.chats_item_confirm_delete_message)
                                    .setPositiveButton(StringAppearanceUtil.applyAppearance(getString(R.string.chats_item_delete), color = getColor(R.color.common_color_ff3737))) { _, _ ->
                                        AdHocSessionLogic.get(accountContext).deleteSession(data.sessionId)
                                    }
                                    .setNegativeButton(R.string.chats_cancel, null)
                                    .show()
                        }
                    }
                }
                .show(lastPoint.x, lastPoint.y)

        return true
    }

    private fun switchNotePin(channelName: String, pin: Boolean) {
        AdHocSessionLogic.get(accountContext).updatePin(channelName, pin)
    }

    private fun createSearchBarSessionData(): AdHocSession {
        return AdHocSession("", "", "")
    }


    class AdHocItemHolder(private val accountContext: AccountContext, view:View):AmeRecycleViewAdapter.ViewHolder<AdHocSession>(view) {

        private val avatar = view.findViewById<AdHocSessionAvatar>(R.id.adhoc_channel_item_avatar)
        private val titleView = view.findViewById<TextView>(R.id.adhoc_channel_item_name)
        private val lastMessageView = view.findViewById<TextView>(R.id.adhoc_channel_item_message)
        private val timestampView = view.findViewById<TextView>(R.id.adhoc_channel_item_time)

        private val pinView = view.findViewById<View>(R.id.adhoc_channel_item_pin)
        private val muteView = view.findViewById<View>(R.id.adhoc_channel_item_mute)
        private val readStatusView = view.findViewById<View>(R.id.adhoc_channel_item_unread_status)

        val lastTouchPoint = Point()

        init {
            view.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    lastTouchPoint.set(event.rawX.toInt(), event.y.toInt() - view.height)
                }
                return@setOnTouchListener false
            }
        }

        override fun setData(data: AdHocSession) {
            super.setData(data)
            val context = itemView.context
            var name = data.displayName(accountContext)
            if (data.isChannel()) {
                name = "$name (${AdHocChannelLogic.get(accountContext).getChannelUserCount(data.sessionId)})"
            }
            titleView.text = name
            avatar.setSession(accountContext, data)

            val  builder = SpannableStringBuilder()
            val text = if(!data.draft.isBlank() && data.unreadCount == 0) {
                builder.append(StringAppearanceUtil.applyAppearance(context.getString(R.string.common_thread_draft), color = AppUtil.getColor(context.resources, R.color.common_color_ff3737)))
                builder.append(" ")
                data.draft
            } else {
                data.lastMessage
            }

            if (data.unreadCount > 0) {
                builder.append(getUnreadCountString(itemView.context, data.unreadCount))
                builder.append(" ")
            }

            if (data.atMe) {
                builder.append(StringAppearanceUtil.applyAppearance(AppContextHolder.APP_CONTEXT.getString(com.bcm.messenger.chats.R.string.chats_at_me_description),
                        color = getColor(com.bcm.messenger.chats.R.color.common_content_warning_color)))
            }

            ALog.i(TAG, "setData session: ${data.sessionId}, lastState: ${data.lastState}")
            if (data.lastState != AdHocSession.STATE_SUCCESS) {
                val d = if (data.lastState == AdHocSession.STATE_SENDING) {
                    context.getDrawable(R.drawable.common_doing_icon)
                }else {
                    context.getDrawable(R.drawable.adhoc_session_failure_icon)
                }
                d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                builder.append(StringAppearanceUtil.addImage("  $text", d, 0))
            }else {
                builder.append(text)
            }

            lastMessageView.text = builder

            val timeString = DateUtils.getThreadMessageTimeSpan(itemView.context, data.timestamp, getSelectedLocale(AppContextHolder.APP_CONTEXT))
            timestampView.text = timeString

            if (data.pin) {
                pinView.visibility = View.VISIBLE
            } else {
                pinView.visibility = View.GONE
            }

            if (data.mute) {
                muteView.visibility = View.VISIBLE
            } else {
                muteView.visibility = View.GONE
            }

            if (data.unreadCount > 0) {
                readStatusView.visibility = View.VISIBLE
                if (data.mute) {
                    readStatusView.setBackgroundResource(R.drawable.adhoc_channel_grey_new_dot)
                } else {
                    readStatusView.setBackgroundResource(R.drawable.adhoc_channel_red_new_dot)
                }
            } else {
                readStatusView.visibility = View.GONE
            }

        }

        private fun getUnreadCountString(context:Context, unreadCount: Int): String {
            return when {
                unreadCount > 99 -> context.resources.getString(R.string.adhoc_unread_full_messages)
                unreadCount == 1 -> context.resources.getString(R.string.adhoc_unread_message)
                unreadCount > 1 -> context.resources.getString(R.string.adhoc_unread_messages, unreadCount)
                else -> ""
            }
        }
    }

    override fun onSessionListChanged() {
        AmeDispatcher.mainThread.dispatch {
            dataSource.updateDataSource(listOf(createSearchBarSessionData()) + AdHocSessionLogic.get(accountContext).getSessionList())
            RxBus.post(HomeTabEvent(accountContext, HomeTabEvent.TAB_ADHOC, false, AdHocSessionLogic.get(accountContext).getUnReadSessionCount()))
        }
    }

    override fun onScanStateChanged(state: AdHocChannelLogic.IAdHocChannelListener.CONNECT_STATE) {
        ALog.i(TAG, "onScanStateChanged: $state")
        AmeDispatcher.mainThread.dispatch {
            refreshSessionList()
            when (state) {
                AdHocChannelLogic.IAdHocChannelListener.CONNECT_STATE.CONNECTED -> {
                    deviceStateListener.refresh()
                    adHocStep.connected()
                }
                AdHocChannelLogic.IAdHocChannelListener.CONNECT_STATE.SCANNING -> {
                    adHocStep.disconnected()
                } else  -> {
                    adHocStep.connecting()
                }
            }
        }
    }

    override fun onDeviceStateChanged(newState: String, error:Boolean) {
        AmeDispatcher.mainThread.dispatch {
            val view = adhoc_main_device_error?:return@dispatch
            view.text = newState
            if (error) {
                if (view.visibility != View.VISIBLE) {
                    view.visibility = View.VISIBLE
                    dataSource.refresh()
                }
            } else {
                view.visibility = View.GONE
                dataSource.refresh()
            }
        }
    }

    override fun onChannelUserChanged(sessionList: List<String>) {
        AmeDispatcher.mainThread.dispatch {
            refreshSessionList()
        }
    }

    override fun onReady() {
        AmeDispatcher.mainThread.dispatch {
            dataSource.updateDataSource(listOf(createSearchBarSessionData()) + AdHocSessionLogic.get(accountContext).getSessionList())
            RxBus.post(HomeTabEvent(accountContext, HomeTabEvent.TAB_ADHOC, false, AdHocSessionLogic.get(accountContext).getUnReadSessionCount()))
        }
    }

    private fun refreshSessionList(delay: Long = 600) {
        val wSelf = WeakReference(this)

        if (disposeRefresh?.isDisposed == false) {
            disposeRefresh?.dispose()
        }

        disposeRefresh = AmeDispatcher.mainThread.dispatch({
            wSelf.get()?.dataSource?.refresh()
        }, delay)
    }
}
