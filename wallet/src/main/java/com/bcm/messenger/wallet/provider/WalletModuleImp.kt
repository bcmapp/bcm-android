package com.bcm.messenger.wallet.provider

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.accountmodule.IWalletModule
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.bcm.messenger.wallet.utils.BCMWalletManager
import com.bcm.route.annotation.Route

/**
 * 钱包对外服务实现
 * Created by wjh on 2018/6/13
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_WALLET_BASE)
class WalletModuleImp : IWalletModule {
    private lateinit var accountContext: AccountContext
    override val context: AccountContext
        get() = accountContext

    override fun setContext(context: AccountContext) {
        this.accountContext = context
    }

    override fun initModule() {

    }

    override fun uninitModule() {

    }

    private val TAG = "WalletProviderImp"

    override fun initWallet(privateKeyArray: ByteArray, password: String) {
        BCMWalletManager.startInitService(AppContextHolder.APP_CONTEXT, privateKeyArray)
    }

    override fun logoutWallet() {
        //登出所有钱包相关的文件和记录
        try {
            ALog.d(TAG, "logoutWallet")
            BCMWalletManager.reset()
            WalletViewModel.recycle()

        } catch (ex: Exception) {
            ALog.e(TAG, "WalletProviderImp logoutWallet error", ex)
        }
    }

    override fun destroyWallet() {
        //删除所有钱包相关的文件和记录
        try {
            logoutWallet()

            ALog.d(TAG, "destroyWallet")
            BCMWalletManager.clear()

        } catch (ex: Exception) {
            ALog.e(TAG, "WalletProviderImp destroyWallet error", ex)
        }
    }

}