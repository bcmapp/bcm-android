package com.bcm.messenger.wallet.presenter

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.utils.BCMWalletManagerContainer
import com.bcm.messenger.wallet.utils.WalletSettings

/**
 * 用于重要事情的通知
 * Created by wjh on 2018/6/1
 */
class ImportantLiveData(private val mManager: BCMWalletManagerContainer.BCMWalletManager) : LiveData<ImportantLiveData.ImportantEvent>(), SharedPreferences.OnSharedPreferenceChangeListener {

    class ImportantEvent(val id: Int, val data: Any?) {
        constructor(id: Int) : this(id, null)
    }

    companion object {

        //表示钱包同步进度
        const val EVENT_SYNC = 1
        //表示钱包是否备份，目前已废弃
        const val EVENT_BACKUP = 2
        //表示新钱包加入或初始化成功，data: BCMWallet
        const val EVENT_NEW = 3
        //表示有新交易结果, data: TransferResult
        const val EVENT_TRANSACTION_RESULT = 4
        //表示有交易变更, data: TransactionDisplay
        const val EVENT_TRANSACTION_NEW = 5
        //表示有名称变更, data: WalletDisplay
        const val EVENT_NAME_CHANGED = 6
        //当前查看的法币值变更, 无data
        const val EVENT_CURRENCY_CHANGED = 7
        //货币汇率更新， 无data
        const val EVENT_RATE_UPDATE = 8
        //钱包删除， data: BCMWallet
        const val EVENT_DELETE = 9
        //钱包余额变更, data: WalletDisplay
        const val EVENT_BALANCE = 10
        //某一币种的钱包列表余额更新
        const val EVENT_BALANCE_LIST = 12
        //账号备份事件, data: Boolean true表示提示备份
        const val EVENT_ACCOUNT_BACKUP = 13

    }

    /**
     * 触发通知
     */
    fun notice(event: ImportantEvent) {
        value = event
    }

    /**
     * 触发通知
     */
    fun noticeDelay(event: ImportantEvent) {
        postValue(event)
    }

    override fun onActive() {
        ALog.d("WalletViewModel", "onActive")
        mManager.getAccountPreferences(AppContextHolder.APP_CONTEXT).registerOnSharedPreferenceChangeListener(this)

    }

    override fun onInactive() {
        ALog.d("WalletViewModel", "onInactive")
        mManager.getAccountPreferences(AppContextHolder.APP_CONTEXT).unregisterOnSharedPreferenceChangeListener(this)
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        //接收到preference配置变更的通知
        when (key) {
            WalletSettings.PREF_COIN_CURRENCY -> {
                //法币单位变更了，通知页面更新
                postValue(ImportantEvent(EVENT_CURRENCY_CHANGED))

            }
            WalletSettings.PREF_BACKUP_NOTICE -> {
                //接收到是否展示备份红点的通知
                val showDot = sharedPreferences.getBoolean(key, true)
                postValue(ImportantEvent(EVENT_BACKUP, showDot))
            }
        }
    }
}