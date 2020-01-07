package com.bcm.messenger.chats.group.setting

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.GroupMemberPhotoView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.centerpopup.AmeCenterPopup
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.AppContextHolder
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference

/**
 
 * Created by bcm.social.01 on 2018/5/31.
 */
object ChatGroupChangeOwnerPopWindow {
    private var newOwner: AmeGroupMemberInfo? = null
    private var groupId: Long = 0

    fun show(accountContext: AccountContext, activity: FragmentActivity?, groupId: Long, new: AmeGroupMemberInfo, selectionResult: (owner: AmeGroupMemberInfo) -> Unit) {
        newOwner = new
        this.groupId = groupId
       
        val weakActivity = WeakReference(activity)
        val viewCreator = object : AmeCenterPopup.CustomViewCreator {
            var avatarView: GroupMemberPhotoView? = null
            var nickView: TextView? = null

            override fun onCreateView(parent: ViewGroup): View? {
                val activity = weakActivity.get()
                var view: View? = null
                if (activity != null && !activity.isFinishing) {
                    view = LayoutInflater.from(activity).inflate(R.layout.chats_group_change_owner, parent, true)

                    avatarView = view.findViewById(R.id.new_owner_avatar) as? GroupMemberPhotoView
                    nickView = view.findViewById(R.id.new_owner_name_text) as? TextView
                    updateUI()

                    val change = view.findViewById(R.id.change_new_owner) as? View
                    change?.setOnClickListener {
                        EventBus.getDefault().postSticky(PleaseReturnTheOwnerEvent {
                            if (it != null) {
                                newOwner = it
                                updateUI()
                            }
                        })

                        val activity1 = weakActivity.get()
                        if (activity1 != null && !activity1.isFinishing && null != newOwner) {
                            activity1.startBcmActivity(accountContext, Intent(activity1, ChatGroupMemberSelectionActivity::class.java).apply {
                                putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, newOwner!!.gid)
                            })
                        }
                    }
                }
                return view
            }

            override fun onDetachView() {
                avatarView?.clearAddressListener()
                avatarView?.setUpdateListener {  }
            }

            fun updateUI() {
                val owner = newOwner
                if (null != owner){
                    val uid = owner.uid

                    val recipient = Recipient.from(accountContext, uid, true)
                   val name = BcmGroupNameUtil.getGroupMemberName(recipient, owner)

                    nickView?.text = name
                    val owner = newOwner
                    if (owner != null) {
                        avatarView?.setAvatar(AmeGroupMemberInfo.MEMBER, Address.from(accountContext, owner.uid), newOwner?.keyConfig)
                    }
                    avatarView?.setUpdateListener {
                        if (it == recipient){
                            nickView?.text = recipient.name
                        }
                    }
                }
            }
        }

        AmePopup.center.newBuilder()
                .withTitle(activity?.resources?.getString(R.string.chats_group_change_owner_content) ?: "")
                .withWarningTitle( activity?.resources?.getString(R.string.chats_item_confirm) ?: "Confirm")
                .withCancelTitle(activity?.resources?.getString(R.string.common_cancel)?:"Cancel")
                .withCustomView(viewCreator)
                .withWarningListener {
                    AmePopup.center.dismiss()
                    if (null != newOwner) {
                        selectionResult(newOwner!!)
                        newOwner = null
                    }
                }.show(activity)
    }

    class PleaseReturnTheOwnerEvent(val gotIt: (newOwner: AmeGroupMemberInfo?) -> Unit)
}