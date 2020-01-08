package com.bcm.messenger.adhoc.ui.channel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import com.bcm.imcore.im.ChannelUserInfo
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.*
import com.bcm.messenger.adhoc.util.AdHocUtil
import com.bcm.messenger.chats.components.recyclerview.WrapContentGridLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.adapter.ListDataSource
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.adhoc_activity_channel_setting.*

/**
 * Created by wjh on 2019/7/30
 */
class AdHocChannelSettingActivity : SwipeBaseActivity(),
        RecipientModifiedListener,
        AmeRecycleViewAdapter.IViewHolderDelegate<ChannelUserInfo>,
        AdHocChannelLogic.IAdHocChannelListener {

    companion object {
        private const val TAG = "AdHocChannelSettingActivity"
    }

    private var lightMode = false
    private var isBgLight = true
    private var mSessionId: String = ""
    private var mSessionInfo: AdHocSession? = null


    private val onLineUserSource = object : ListDataSource<ChannelUserInfo>() {
        override fun getItemId(position: Int): Long {
            return EncryptUtils.byteArrayToLong(getData(position).uid.toByteArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.adhoc_activity_channel_setting)
        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        AdHocChannelLogic.get(accountContext).removeListener(this)
    }

    private fun initView() {
        mSessionId = intent.getStringExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION) ?: ""
        mSessionInfo = AdHocSessionLogic.get(accountContext).getSession(mSessionId)
        initNavigationBar()
        updateChannelView()
        AdHocChannelLogic.get(accountContext).addListener(this)

        channel_invite_item.setOnClickListener {
            val channel = AdHocChannelLogic.get(accountContext).getChannel(mSessionInfo?.cid ?: "")
            if (channel != null) {
                startBcmActivity(Intent(this, AdHocInviteJoinActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.ADHOC.CID, channel.cid)
                    putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, mSessionId)
                })
            } else {
                ALog.w(TAG, "invite to join channel fail, channel is null")
            }
        }

        channel_pin_item.setSwitchEnable(false)
        channel_pin_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val pin = !channel_pin_item.getSwitchStatus()
            AdHocSessionLogic.get(accountContext).updatePin(mSessionId, pin)
            channel_pin_item.setSwitchStatus(pin)
        }

        channel_mute_item.setSwitchEnable(false)
        channel_mute_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val mute = !channel_mute_item.getSwitchStatus()
            AdHocSessionLogic.get(accountContext).updateMute(mSessionId, mute)
            channel_mute_item.setSwitchStatus(mute)
        }

        channel_clear_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            AmePopup.bottom.newBuilder()
                    .withTitle(getString(R.string.adhoc_channel_setting_clear_notice))
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_clear), AmeBottomPopup.PopupItem.CLR_RED) {
                        AdHocMessageLogic.get(accountContext).getModel()?.clearHistory() {
                            ALog.i(TAG, "clear local history result: $it")
                            if (it) {
                                AmePopup.result.succeed(this, getString(R.string.adhoc_channel_setting_clear_success))
                            } else {
                                AmePopup.result.failure(this, getString(R.string.adhoc_channel_setting_clear_fail))

                            }
                        }
                    })
                    .withDoneTitle(getString(R.string.chats_cancel))
                    .show(this)

        }

        channel_leave_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            AmePopup.bottom.newBuilder()
                    .withTitle(getString(R.string.adhoc_channel_setting_leave_notice))
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.adhoc_channel_setting_leave_action), AmeBottomPopup.PopupItem.CLR_RED) {
                        AdHocMessageLogic.get(accountContext).getModel()?.leaveChannel() {
                            ALog.i(TAG, "leave session result: $it")
                            if (it) {
                                AmePopup.result.succeed(this, getString(R.string.adhoc_channel_setting_leave_success)) {
                                    setResult(Activity.RESULT_OK)
                                    finish()
                                }
                            } else {
                                AmePopup.result.failure(this, getString(R.string.adhoc_channel_setting_leave_fail))

                            }
                        }
                    })
                    .withDoneTitle(getString(R.string.chats_cancel))
                    .show(this)
        }
    }


    private fun updateMemberList() {
        val list = AdHocChannelLogic.get(accountContext).getChannelUserList(mSessionId)
        if (list.size <= 10) {
            onLineUserSource.updateDataSource(list)
        } else {
            onLineUserSource.updateDataSource(list.subList(0, 10))
        }
        adhoc_group_member_count.text = "${list.size}"
    }


    private fun initNavigationBar() {

        window.setTransparencyBar(false)
        channel_setting_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }
        })

        val scrollHeight = 160.dp2Px()
        channel_setting_title.setTitleBarAlpha(0f)
        channel_scroll_view.setOnScrollChangeListener { _: NestedScrollView?, _, scrollY, _, _ ->
            val alpha = if (scrollY >= scrollHeight) 1f else scrollY / scrollHeight.toFloat()
            channel_setting_title.setTitleBarAlpha(alpha)

            if (alpha >= 0.5f && !lightMode) {
                lightMode = true
                window.setStatusBarLightMode()
                channel_setting_title.setLeftIcon(R.drawable.common_back_arrow_black_icon)
            } else if (alpha < 0.5f && lightMode) {
                lightMode = false
                if (isBgLight) {
                    window.setStatusBarLightMode()
                } else {
                    window.setStatusBarDarkMode()
                    channel_setting_title.setLeftIcon(R.drawable.common_back_arrow_white_icon)
                }
            }
        }
    }


    private fun updateChannelView() {
        val session = mSessionInfo ?: return

        if (mSessionInfo?.isChannel() == true && AdHocChannelLogic.get(accountContext).getChannel(session.cid)?.channelName == AdHocChannel.OFFICIAL_CHANNEL) {
            channel_leave_item?.visibility = View.GONE
        } else {
            channel_leave_item?.visibility = View.VISIBLE
        }


        session.getChatRecipient()?.addListener(this)
        val name = session.displayName(accountContext)
        channel_control_name?.text = name
        channel_setting_title?.setCenterText(name)

        channel_avatar_layout.setSession(accountContext, session) { bitmap ->
            if (bitmap != null) {
                channel_header_layout?.setGradientBackground(bitmap) {
                    isBgLight = it
                    if (it) {
                        window.setStatusBarLightMode()
                        channel_control_name.setTextColor(getColorCompat(R.color.common_color_black))
                    } else {
                        channel_setting_title.setLeftIcon(R.drawable.common_back_arrow_white_icon)
                        channel_control_name.setTextColor(getColorCompat(R.color.common_color_white))
                    }
                }
            }
        }

        channel_pin_item.setSwitchStatus(session.pin)
        channel_mute_item.setSwitchStatus(session.mute)

        if (!session.isChannel()) {
            channel_invite_item.visibility = View.GONE
            group_member_recycler_view.visibility = View.GONE
            adhoc_group_members_item.visibility = View.GONE

        } else {
            if (session.cid == AdHocUtil.officialCid()) {

                channel_leave_item.visibility = View.GONE
            }
            channel_invite_item.visibility = View.VISIBLE
            group_member_recycler_view.visibility = View.VISIBLE
            adhoc_group_members_item.visibility = View.VISIBLE

            group_member_recycler_view.layoutManager = WrapContentGridLayoutManager(this, 5)
            val adapter = AmeRecycleViewAdapter(this, onLineUserSource)
            adapter.setViewHolderDelegate(this)
            adapter.setHasStableIds(true)
            group_member_recycler_view.adapter = adapter

            adhoc_group_members_item.setOnClickListener {
                if (QuickOpCheck.getDefault().isQuick) {
                    return@setOnClickListener
                }

                val intent = Intent(it.context, AdHocChannelMemberListActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, mSessionId)
                }
                startBcmActivity(intent)
            }

            updateMemberList()
        }

    }

    override fun onChannelUserChanged(sessionList: List<String>) {
        AmeDispatcher.mainThread.dispatch {
            if (sessionList.contains(mSessionId)) {
                if (mSessionInfo?.isChannel() == true) {
                    updateMemberList()
                }
                channel_avatar_layout?.setSession(accountContext,mSessionInfo ?: return@dispatch)
            }
        }
    }

    override fun onModified(recipient: Recipient) {
        if (recipient.address.serialize() == mSessionInfo?.uid) {
            mSessionInfo?.let {
                val name = it.displayName(recipient.address.context())
                channel_control_name?.text = name
                channel_setting_title?.setCenterText(name)
            }
        }
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<ChannelUserInfo>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<ChannelUserInfo> {
        return MemberHolder(inflater.inflate(R.layout.adhoc_channel_setting_list_avatar, parent, false))
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<ChannelUserInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<ChannelUserInfo>) {
        if (QuickOpCheck.getDefault().isQuick) {
            return
        }
        val holder = viewHolder as MemberHolder
        val address = Address.from(accountContext, holder.getData()?.uid ?: return)
        if (address.isCurrentLogin) {
            return
        }
        AmeModuleCenter.contact(address.context())?.openContactDataActivity(this, address, holder.getData()?.name)
    }


    inner class MemberHolder(view: View) : AmeRecycleViewAdapter.ViewHolder<ChannelUserInfo>(view) {
        private val avatar = view.findViewById<IndividualAvatarView>(R.id.adhoc_member_avatar)
        private val name = view.findViewById<EmojiTextView>(R.id.adhoc_member_nickname)
        override fun setData(data: ChannelUserInfo) {
            super.setData(data)

            avatar.setPhoto(Recipient.from(accountContext, data.uid, true), data.name, IndividualAvatarView.DEFAULT_PHOTO_TYPE)
            name.text = data.name
        }
    }
}