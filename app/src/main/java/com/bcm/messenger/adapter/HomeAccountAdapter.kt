package com.bcm.messenger.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.ui.HomeAddAccountView
import com.bcm.messenger.ui.HomeProfileView
import com.bcm.messenger.ui.widget.IProfileView
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by Kin on 2019/12/31
 */
class HomeAccountAdapter(private val context: Context) : PagerAdapter() {
    interface AdapterListener {
        fun onViewClickedClose()
        fun onAccountLoadSuccess()
        fun onViewClickLogin(uid: String)
        fun onViewClickDelete(uid: String)
    }

    private val TAG = "HomeAccountAdapter"
    private val accountList = mutableListOf<HomeAccountItem>()
    private val views = mutableMapOf<Int, IProfileView>()
    var lastActivePos = 0
        private set

    var listener: AdapterListener? = null

    private val width = AppContextHolder.APP_CONTEXT.getScreenWidth() - 120.dp2Px()
    private val height = if (AppContextHolder.APP_CONTEXT.checkDeviceHasNavigationBar()) {
        getRealScreenHeight() - AppContextHolder.APP_CONTEXT.getNavigationBarHeight()
    } else {
        getRealScreenHeight()
    } - 120.dp2Px()

    init {
        loadAccounts()
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val account = accountList[position]
        val view: IProfileView = if (account.type == TYPE_ADD) {
            HomeAddAccountView(context)
        } else {
            HomeProfileView(context).apply {
                setAccountItem(account)
                isLogin = account.type == TYPE_ONLINE
                isActive = position == lastActivePos
//                if (position == lastActivePos) {
//                    isActive = true
//                }
                setListener(object : HomeProfileView.ProfileViewCallback {
                    override fun onClickExit() {
                        listener?.onViewClickedClose()
                    }

                    override fun onClickDelete(uid: String) {
                        accountList.forEach {
                            if (it.account.uid == uid) {
                                listener?.onViewClickDelete(it.account.uid)
                            }
                        }
                    }

                    override fun onClickLogin(uid: String) {
                        listener?.onViewClickLogin(uid)
                    }

                    override fun checkPosition(): Float {
                        return when {
                            position < lastActivePos -> -1f
                            position == lastActivePos -> 0f
                            else -> 1f
                        }
                    }
                })
            }
        }
        view.initView()
        when {
            position < lastActivePos -> view.positionChanged(-1f)
            position == lastActivePos -> view.positionChanged(0f)
            position > lastActivePos -> view.positionChanged(1f)
        }
        (view as View).tag = Pair(position, account.account.uid)

        container.addView(view as View, ViewGroup.LayoutParams(width, height))
        views[position] = view
        return view
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun getCount(): Int {
        return accountList.size
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
        views.remove(position)
    }

    override fun getItemPosition(`object`: Any): Int {
        return POSITION_NONE
    }

    fun setActiveView(currentPos: Int) {
        views[lastActivePos]?.isActive = false
        views[currentPos]?.isActive = true
        lastActivePos = currentPos
    }

    fun getCurrentShownViews(currentPos: Int): List<IProfileView> {
        return views.values.toList()
    }

    fun getCurrentView(currentPos: Int): IProfileView? {
        return views[currentPos]
    }

    fun checkIndex(uid: String): Int {
        accountList.forEachIndexed { index, it ->
            if (it.account.uid == uid) {
                return index
            }
        }
        return -1
    }

    @SuppressLint("CheckResult")
    fun loadAccounts() {
        Observable.create<List<HomeAccountItem>> {
            val dataList = mutableListOf<HomeAccountItem>()
            val list = AmeLoginLogic.getAccountList()
            dataList.add(HomeAccountItem(TYPE_ADD, AmeAccountData().apply { uid = "ADD" }, AccountContext("","","")))
            list.forEach { accountData ->
                val accountContext = AmeModuleCenter.login().getAccountContext(accountData.uid)
                val type = if (accountContext.isLogin) {
                    TYPE_ONLINE
                } else {
                    TYPE_OFFLINE
                }
                dataList.add(HomeAccountItem(type, accountData, accountContext))
            }
            it.onNext(dataList)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    accountList.clear()
                    accountList.addAll(it)
                    notifyDataSetChanged()
                    listener?.onAccountLoadSuccess()
                }, {
                    ALog.e(TAG, "Load accounts error", it)
                })
    }

    fun deleteAccount(uid: String) {
        var isChanged = false
        for (item in accountList) {
            if (item.account.uid == uid) {
                AmeLoginLogic.accountHistory.deleteAccount(uid)
                accountList.remove(item)
                isChanged = true
                break
            }
        }
        if (isChanged) {
            views.values.forEach {
                if ((it as View).tag == uid) {
                    it.tag = ""
                }
            }
            if (lastActivePos >= accountList.size) {
                lastActivePos--
            }
            notifyDataSetChanged()
        }
    }
}