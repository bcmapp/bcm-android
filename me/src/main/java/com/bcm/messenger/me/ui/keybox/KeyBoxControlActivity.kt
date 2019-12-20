package com.bcm.messenger.me.ui.keybox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.bean.KeyBoxItem
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_activity_keybox_control.*
import kotlinx.android.synthetic.main.me_item_keybox_account.view.*
import kotlinx.android.synthetic.main.me_item_keybox_title.view.*

/**
 * Created by zjl on 2018/8/15.
 */
@Route(routePath = ARouterConstants.Activity.ME_KEYBOX)
class KeyBoxControlActivity : SwipeBaseActivity() {

    private val TAG = "KeyBoxControlActivity"
    private val REQUEST_VERIFICATION_SCAN = 1001

    private val adapter = KeyboxAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_keybox_control)
        initView()

    }

    override fun onResume() {
        super.onResume()
        loadKeyBoxList()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VERIFICATION_SCAN && resultCode == Activity.RESULT_OK) {
            handleQrScan(data?.getStringExtra(ARouterConstants.PARAM.SCAN.SCAN_RESULT))
        }
    }

    fun initView() {
        keybox_control_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }

            override fun onClickRight() {
                if (AmeLoginLogic.isAccountHistoryReachLimit()) {
                    AmePopup.center.newBuilder()
                            .withTitle(resources.getString(R.string.me_keybox_reach_limit))
                            .withContent(resources.getString(R.string.me_keybox_reach_limit_detail))
                            .withOkTitle(resources.getString(R.string.common_popup_ok))
                            .show(this@KeyBoxControlActivity)
                } else {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.SCAN_NEW)
                            .putString(ARouterConstants.PARAM.SCAN.SCAN_CHARSET, "utf-8")
                            .putInt(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_ACCOUNT)
                            .navigation(this@KeyBoxControlActivity, REQUEST_VERIFICATION_SCAN)
                }
            }

            override fun onClickCenter() {
                if (!AppUtil.isReleaseBuild()) {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.APP_DEV_SETTING).navigation(this@KeyBoxControlActivity)
                }
            }
        })
        keybox_accounts_list.layoutManager = LinearLayoutManager(this)
        keybox_accounts_list.adapter = adapter


        if (!AppUtil.isReleaseBuild()) {
            env_test_layout.visibility = View.VISIBLE
            env_export_account_list.setOnClickListener {
                AmeLoginLogic.accountHistory.export()
                ToastUtil.show(this@KeyBoxControlActivity, "导出成功")
            }

            env_import_account_list.setOnClickListener {
                AmeLoginLogic.accountHistory.import()
                ToastUtil.show(this@KeyBoxControlActivity, "导入成功")
            }
        }

    }

    private fun loadKeyBoxList() {

        Observable.create<List<KeyBoxItem>> {
            val dataList = mutableListOf<KeyBoxItem>()
            if (AmeLoginLogic.isLogin()) { //判断是否已经登录
                val list = AmeLoginLogic.getAccountList()
                if (list.isNotEmpty()) {
                    dataList.add(KeyBoxItem(KeyBoxItem.CURRENT_ITEM_TITLE, Unit)) //添加顶部
                    if (list.size == 1) {
                        dataList.add(KeyBoxItem(KeyBoxItem.ACCOUNT_DATA, list[0]))
                    } else {
                        dataList.add(KeyBoxItem(KeyBoxItem.ACCOUNT_DATA, list[0]))
                        dataList.add(KeyBoxItem(KeyBoxItem.INACTIVE_ITEM_TITLE, Unit))
                        list.forEachIndexed { index, any ->
                            if (index != 0) {
                                dataList.add(KeyBoxItem(KeyBoxItem.ACCOUNT_DATA, any))
                            }
                        }
                    }
                }

            } else { //非登录
                dataList.add(KeyBoxItem(KeyBoxItem.INACTIVE_ITEM_TITLE, Unit))
                val list = AmeLoginLogic.getAccountList()
                list.forEach { any ->
                    dataList.add(KeyBoxItem(KeyBoxItem.ACCOUNT_DATA, any))
                }
            }
            it.onNext(dataList)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    adapter.setData(it)
                    if (it.isEmpty()) {
                        finish()
                    }
                }, {
                    ALog.e(TAG, "loadKeyBoxList error", it)
                })

    }

    private fun handleQrScan(qrCode: String?) {
        AmeLoginLogic.saveBackupFromExportModelWithWarning(qrCode ?: return) { accountId ->
            if (!accountId.isNullOrEmpty()){
                loadKeyBoxList()
            }
        }
    }

    private inner class KeyboxAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val dataList = mutableListOf<KeyBoxItem>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                KeyBoxItem.CURRENT_ITEM_TITLE, KeyBoxItem.INACTIVE_ITEM_TITLE -> {
                    val view = layoutInflater.inflate(R.layout.me_item_keybox_title, parent, false)
                    KeyBoxTitleViewHolder(view)
                }
                else -> {
                    val view = layoutInflater.inflate(R.layout.me_item_keybox_account, parent, false)
                    KeyBoxAccountViewHolder(view)
                }
            }
        }

        override fun getItemCount(): Int {
            return dataList.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val keyBoxItem = dataList[position]
            when (holder) {
                is KeyBoxTitleViewHolder -> {
                    holder.bindData(keyBoxItem)
                }
                is KeyBoxAccountViewHolder -> {
                    val data = keyBoxItem.data
                    if (data is AmeAccountData) {
                        holder.bindAccountData(data)
                    }
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return dataList[position].type
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is KeyBoxAccountViewHolder) {
                holder.unBindData()
            }
        }

        fun removeAllData() {
            if (dataList.isNotEmpty()) {
                dataList.clear()
                notifyDataSetChanged()
            }
        }

        fun setData(list: List<KeyBoxItem>) {
            dataList.clear()
            dataList.addAll(list)
            notifyDataSetChanged()
        }
    }



    private inner class KeyBoxTitleViewHolder(private val containerView: View) : RecyclerView.ViewHolder(containerView) {
        fun bindData(data: KeyBoxItem) {
            if (data.type == KeyBoxItem.CURRENT_ITEM_TITLE) {
                containerView.keybox_sort_title.text = getString(R.string.me_keybox_current_item)
            } else if (data.type == KeyBoxItem.INACTIVE_ITEM_TITLE) {
                containerView.keybox_sort_title.text = getString(R.string.me_keybox_inactive_item)
            }
        }
    }

    private inner class KeyBoxAccountViewHolder(private val containerView: View) : RecyclerView.ViewHolder(containerView), RecipientModifiedListener {

        private var recipient: Recipient? = null
        private var accountData: AmeAccountData? = null

        private var x = 0
        private var y = 0

        init {
            containerView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    x = event.rawX.toInt()
                    y = event.y.toInt() - containerView.height
                }
                return@setOnTouchListener false
            }

            containerView.setOnClickListener {
                if (QuickOpCheck.getDefault().isQuick){
                    return@setOnClickListener
                }

                val id = accountData?.getAccountID()
                if (!AMESelfData.isLogin) { //非登录状态

                    startActivity(Intent(this@KeyBoxControlActivity, VerifyKeyActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(VerifyKeyActivity.BACKUP_JUMP_ACTION, VerifyKeyActivity.LOGIN_PROFILE)
                        putExtra(RegistrationActivity.RE_LOGIN_ID, id ?: "")
                    })

                } else {
                    SwitchAccountAdapter().switchAccount(it.context, id ?: "", recipient)
                }
            }

            containerView.setOnLongClickListener {

                if (AmeLoginLogic.getCurrentAccount()?.uid != accountData?.getAccountID()) {
                    BcmPopupMenu.Builder(this@KeyBoxControlActivity)
                            .setMenuItem(listOf(BcmPopupMenu.MenuItem(getString(R.string.me_keybox_long_click_delete))))
                            .setAnchorView(containerView)
                            .setSelectedCallback {
                                gotoDeleteVerify()
                            }
                            .show(x, y)
                }
                return@setOnLongClickListener true
            }

            containerView.keybox_account_qrcode.setOnClickListener {
                if (QuickOpCheck.getDefault().isQuick){
                    return@setOnClickListener
                }
                val id = accountData?.getAccountID()
                if (id != null) {
                    startActivity(Intent(this@KeyBoxControlActivity, VerifyKeyActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(VerifyKeyActivity.BACKUP_JUMP_ACTION, VerifyKeyActivity.SHOW_QRCODE_BACKUP)
                        putExtra(VerifyKeyActivity.ACCOUNT_ID, id)
                    })
                }
            }
        }

        fun bindAccountData(account: AmeAccountData) {
            accountData = account
            ALog.d(TAG, "bindAccountData version: ${account.version}, uid: ${account.uid}, name: ${account.name}, avatar: ${account.avatar}")

            containerView.keybox_account_name.text = account.name
            containerView.keybox_account_openid.text = "${getString(R.string.me_id_title)}: ${account.uid}"
            if (account.genKeyTime > 0) {
                containerView.key_generate_date.text = getString(R.string.me_str_generation_key_date, DateUtils.formatDayTime(account.genKeyTime))
            }
            val backupTime = AmeLoginLogic.accountHistory.getBackupTime(account.uid)
            if (backupTime > 0) {
                containerView.key_backup_date.text = getString(R.string.me_str_backup_date_import, DateUtils.formatDayTime(backupTime))
                containerView.key_backup_date.setTextColor(getColorCompat(R.color.common_color_white))
            } else {
                containerView.key_backup_date.text = getString(R.string.me_not_backed_up)
                containerView.key_backup_date.setTextColor(getColorCompat(R.color.common_color_ff3737))
            }

            if (AmeLoginLogic.getCurrentAccount()?.uid == account.uid) {
                containerView.setBackgroundResource(R.drawable.me_keybox_account_item_now)
            } else {
                containerView.setBackgroundResource(R.drawable.me_keybox_account_item_other)
            }

            if (account.version > AmeAccountData.V2 || account.uid.isNotEmpty()) {
                val r = Recipient.from(containerView.context, Address.fromSerialized(account.uid), true)
                this.recipient = r
                r.addListener(this)
                r.setProfile(account.name, account.avatar)
                containerView.keybox_account_img.setPhoto(r, account.name, IndividualAvatarView.KEYBOX_PHOTO_TYPE)

            }else {
                if (account.avatar.isNotEmpty()) {
                    containerView.keybox_account_img.requestPhoto(account.avatar, R.drawable.common_recipient_photo_default_small, R.drawable.common_recipient_photo_default_small)
                }else {
                    containerView.keybox_account_img.setPhoto(null, account.name, IndividualAvatarView.KEYBOX_PHOTO_TYPE)
                }
            }
        }

        fun unBindData() {
            recipient?.removeListener(this)
        }

        private fun gotoDeleteVerify() {

            startActivity(Intent(this@KeyBoxControlActivity, VerifyKeyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(VerifyKeyActivity.BACKUP_JUMP_ACTION, VerifyKeyActivity.DELETE_PROFILE)
                putExtra(VerifyKeyActivity.ACCOUNT_ID, accountData?.getAccountID())
            })
        }

        override fun onModified(recipient: Recipient) {
            ALog.d(TAG, "onModified uid: ${recipient.address}")
            if (this.recipient == recipient) {
                ALog.d(TAG, "onModified uid: ${recipient.address} same")
                val localAccount = accountData
                if (localAccount != null) {
                    this.recipient?.setProfile(localAccount.name, localAccount.avatar)
                }
                containerView.keybox_account_img.setPhoto(recipient, localAccount?.name, IndividualAvatarView.KEYBOX_PHOTO_TYPE)
            }
        }
    }

}