package com.bcm.messenger.chats.group.setting

import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.ui.CommonSettingItem
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.common.utils.saveTextToBoard
import kotlinx.android.synthetic.main.chats_group_activity_edit_group_info.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 
 * Created by bcm.social.01 on 2018/5/29.
 */
class ChatGroupProfileActivity : SwipeBaseActivity() {
    companion object {
        private const val TAG = "GroupInfoEditActivity"

        const val ROLE = "role"
    }

    private lateinit var groupModel: GroupViewModel
    private var role = AmeGroupMemberInfo.VISITOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_group_activity_edit_group_info)

        val groupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)
        role = intent.getLongExtra(ROLE, AmeGroupMemberInfo.VISITOR)

        EventBus.getDefault().register(this)

        val groupModel = GroupLogic.getModel(groupId)
        if (null == groupModel) {
            finish()
            return
        }
        this.groupModel = groupModel

        group_profile_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        group_profile_avatar_item.setOnClickListener {
            val intent = Intent(this, ChatGroupAvatarActivity::class.java)
            intent.putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
            intent.putExtra(ARouterConstants.PARAM.PARAM_GROUP_ROLE, role)
            startActivity(intent)
        }

        if (role == AmeGroupMemberInfo.OWNER) {
            group_profile_name_item.setOnClickListener {
                val intent = Intent(this, ChatGroupEditNameActivity::class.java)
                intent.putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
                startActivity(intent)
            }
        } else {
            group_profile_name_item.showRightStatus(CommonSettingItem.RIGHT_NONE)
        }

        if (!isReleaseBuild()) {
            group_profile_id_item.setOnLongClickListener {
                saveTextToBoard(groupId.toString())
                ToastUtil.show(this, "Group ID has been copied to clipboard")
                return@setOnLongClickListener true
            }
        }

        updateGroupInfo()
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    private fun updateGroupInfo() {
        val groupInfo = groupModel.getGroupInfo()

        group_profile_name_item.setTip(groupInfo.name)
        group_avatar.showGroupAvatar(groupInfo.gid, false)
        group_profile_id_item.setTip(groupInfo.gid.toString())
    }


    @Subscribe
    fun onEvent(event: GroupViewModel.GroupInfoChangedEvent) {
        updateGroupInfo()
    }

}