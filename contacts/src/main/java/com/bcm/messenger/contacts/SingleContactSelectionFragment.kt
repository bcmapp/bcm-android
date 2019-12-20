package com.bcm.messenger.contacts

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.api.IContactsAction
import com.bcm.messenger.common.api.IContactsCallback
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.ContentShadeView
import com.bcm.messenger.common.ui.CustomDataSearcher
import com.bcm.messenger.common.ui.Sidebar
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.contacts.adapter.ContactsListLoader
import com.bcm.messenger.contacts.components.RecipientRecyclerView
import com.bcm.messenger.contacts.components.RecipientSelectionView
import com.bcm.messenger.contacts.components.SelectionEnableChecker
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/**
 * Created by zjl on 2018/4/8.
 */
@Route(routePath = ARouterConstants.Fragment.SELECT_SINGLE)
class SingleContactSelectionFragment : Fragment(), IContactsAction {

    companion object {
        private val TAG = "SingleContactSelection"
        const val LOADER_CONTACTS = 1
    }

    private var mCallback: IContactsCallback? = null

    private val mHeaderList: MutableList<View> = ArrayList()
    private val mFooterList: MutableList<View> = ArrayList()
    private val mHeaderFooterMap: MutableMap<View, Int> = mutableMapOf()

    private var mRecyclerView: RecipientRecyclerView? = null
    private var mSidebar: Sidebar? = null
    private var mCustomDataSearcher: CustomDataSearcher<Recipient>? = null
    private var mSelectionView: RecipientSelectionView? = null

    private var mEmptyView: ContentShadeView? = null
    private var dispose: Disposable? = null

    private var mFromGroup: Boolean = false
    private var mIncludeMe: Boolean = false

    private var mHeaderSearch = 0
    private var mHeaderEmpty = 0

    private var mShowDecoration: Boolean = false

    private var mFixedSelectedList: MutableList<Recipient> = mutableListOf()

    private var mContactLoader = object : LoaderManager.LoaderCallbacks<List<Recipient>> {

        override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<Recipient>> {
            return ContactsListLoader(AppContextHolder.APP_CONTEXT, mFromGroup, mIncludeMe)
        }

        override fun onLoadFinished(loader: Loader<List<Recipient>>, data: List<Recipient>) {
            mCustomDataSearcher?.setSourceList(data)
            updateContactsListView(data, "",  !data.isEmpty(), true)
        }

        override fun onLoaderReset(loader: Loader<List<Recipient>>) {
        }

    }

    private fun updateContactsListView(recipientList: List<Recipient>?, filter: String, showSidebar: Boolean, showDecoration: Boolean) {
        val isEmpty = recipientList == null || recipientList.isEmpty()
        if (mHeaderEmpty != 0) {
            when {
                mFromGroup -> mEmptyView?.showContent(getString(R.string.contacts_group_empty_text), "")
                else -> mEmptyView?.showContent(getString(R.string.contacts_empty_title_text), "")
            }
            mRecyclerView?.showHeader(mHeaderEmpty, isEmpty)
        }

        mRecyclerView?.setDataList(recipientList, filter, showDecoration = showDecoration)

        if (showSidebar) {
            mSidebar?.show()
        } else {
            mSidebar?.hide()
        }
    }

    private var mRemoteCallback: IContactsAction.QueryResultCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mFromGroup = arguments?.getBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_CONTACT_GROUP, false) ?: false
        mIncludeMe = arguments?.getBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_INCLUDE_ME, false) ?: false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dispose != null && dispose?.isDisposed == false) {
            dispose?.dispose()
        }
        mCustomDataSearcher?.recycle()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        ALog.i(TAG, "onCreateView")
        val convertView = inflater.inflate(R.layout.contacts_fragment_selection, container, false)

        mSelectionView = convertView.findViewById(R.id.contacts_select_top)
        mSelectionView?.setOnContactsActionListener(object : RecipientSelectionView.OnContactsActionListener {
            override fun onDeselected(recipient: Recipient) {
                if (mSelectionView?.visibility == View.VISIBLE) {
                    setSelected(recipient, false)
                }
            }

            override fun onSelected(recipient: Recipient) {
            }

        })

        mRecyclerView = convertView.findViewById(R.id.contacts_select_list)

        val checker = arguments?.getString(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_ENABLE_CHECKER)
                ?:ARouterConstants.PARAM.CONTACTS_SELECT.ENABLE_CHECKER.CHECKER_DEFAULT

        mRecyclerView?.setEnableChecker(SelectionEnableChecker.getChecker(checker))
        mRecyclerView?.setMultiSelect(arguments?.getBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, false) ?: false)
        mRecyclerView?.setCanChangeMode(arguments?.getBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_CHANGE_MODE, false) ?: false)
        mShowDecoration = arguments?.getBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_SHOW_DECORATION, true) ?: true

        mRecyclerView?.setOnContactsActionListener(object : RecipientRecyclerView.OnContactsActionListener {

            override fun onDeselected(recipient: Recipient) {
                mCallback?.onDeselect(recipient)
                mSelectionView?.removeSelection(recipient)
                val isSearching = mCustomDataSearcher?.getSearchText()?.isNotEmpty() == true
                if (isSearching) {
                    mCustomDataSearcher?.setSearchText("")
                    updateContactsListView(mCustomDataSearcher?.getSourceList(), "", true, mShowDecoration)
                }
            }

            override fun onSelected(recipient: Recipient) {
                mCallback?.onSelect(recipient)
                mSelectionView?.addSelection(recipient)
                val isSearching = mCustomDataSearcher?.getSearchText()?.isNotEmpty() == true
                if (isSearching) {
                    mCustomDataSearcher?.setSearchText("")
                    updateContactsListView(mCustomDataSearcher?.getSourceList(), "", true, mShowDecoration)
                }
            }

            override fun onModeChanged(multiSelect: Boolean) {
                ALog.i(TAG, "onModeChanged: $multiSelect")
                mSelectionView?.clearSelection()
                mSelectionView?.visibility = if (multiSelect) View.VISIBLE else View.GONE
                mCallback?.onModeChanged(multiSelect)
            }

        })
        mRecyclerView?.setShowSideBar(true)

        val customDataSearcher = mCustomDataSearcher
        if (customDataSearcher != null) {
            mHeaderSearch = mRecyclerView?.addHeader(customDataSearcher) ?: 0
        }

        if (mRecyclerView?.isMultiSelect() == true) {
            mSelectionView?.visibility = View.VISIBLE
        }

        var index: Int?
        for (header in mHeaderList) {
            index = mRecyclerView?.addHeader(header, false)
            if (index != null) {
                mHeaderFooterMap[header] = index
            }
        }
        for (footer in mFooterList) {
            index = mRecyclerView?.addFooter(footer, false)
            if (index != null) {
                mHeaderFooterMap[footer] = index
            }
        }

        val emptyView = mEmptyView
        if (emptyView != null) {
            mHeaderEmpty = mRecyclerView?.addHeader(emptyView) ?: 0
        }

        mRecyclerView?.notifyDataChanged()

        val sidebar = convertView.findViewById<Sidebar>(R.id.contacts_select_sidebar)
        sidebar.setLetterList(Recipient.LETTERS.toList())
        sidebar.setFastScrollHelper(mRecyclerView!!, mRecyclerView!!)
        mSidebar = sidebar

        initSelectedContacts()
        return convertView

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ALog.i(TAG, "onViewCreated")
        initData()
    }

    private fun initSelectedContacts() {
        //判断是否已有选中的联系人
        mRecyclerView?.setSelectedRecipient(mFixedSelectedList)
        val gid = arguments?.getLong(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)?:return
        if (gid <= 0) {
            return
        }
        val memberList = AmeModuleCenter.group().getMembersFromCache(gid)
        if (memberList.isNotEmpty()) {

            dispose = Observable.create(ObservableOnSubscribe<List<Recipient>> {

                it.onNext(memberList.map { address ->
                    Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(address), true)
                })
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        mRecyclerView?.setSelectedRecipient(it)
                    }
        }

    }

    private fun initData() {
        if (mHeaderEmpty != 0) {
            mEmptyView?.showLoading()
            mRecyclerView?.showHeader(mHeaderEmpty, true, true)
        }
        mSidebar?.hide()
        LoaderManager.getInstance(this).initLoader(LOADER_CONTACTS, Bundle(), mContactLoader)
    }

    override fun setMultiMode(multiSelect: Boolean) {
        mRecyclerView?.setMultiSelect(multiSelect, true)
    }

    override fun addSearchBar(context: Context) {
        if (mCustomDataSearcher == null) {
            val searchbar = CustomDataSearcher<Recipient>(context)
            searchbar.setOnSearchActionListener(object : CustomDataSearcher.OnSearchActionListener<Recipient>() {
                override fun onSearchNull(results: List<Recipient>) {
                    updateContactsListView(results, "", results.isNotEmpty(), mShowDecoration)
                }

                override fun onSearchResult(filter: String, results: List<Recipient>) {
                    updateContactsListView(results, filter, false, false)
                }

                override fun onMatch(data: Recipient, compare: String): Boolean {
                    return Recipient.match(compare, data)
                }

            })

            mCustomDataSearcher = searchbar
        }
    }

    override fun addEmptyShade(context: Context) {

        if (mEmptyView == null) {
            val emptyView = ContentShadeView(context)
            emptyView.setTitleAppearance(16.dp2Px(), context.getColorCompat(R.color.common_content_second_color))
            emptyView.setSubTitleAppearance(12.dp2Px(), Color.parseColor("#C2C2C2"))
            emptyView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            emptyView.setPadding(0, 70.dp2Px(), 0, 50.dp2Px())
            mEmptyView = emptyView
        }
    }


    override fun queryContacts(filter: String, callback: IContactsAction.QueryResultCallback?) {
        Observable.create(ObservableOnSubscribe<List<Recipient>> { emitter ->
            try {
                val dataList = mRecyclerView?.getDataList() ?: listOf()
                emitter.onNext(dataList.filter { it.name.contains(filter, true) })
            } finally {
                emitter.onComplete()
            }
        }).subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { recipientList ->
                    callback?.onQueryResult(recipientList)
                }
    }

    override fun queryContactsFromRemote(address: String, callback: IContactsAction.QueryResultCallback) {
        mRemoteCallback = callback
    }

    override fun addHeader(header: View) {
        val index = mRecyclerView?.addHeader(header, true)
        if (index != null) {
            mHeaderFooterMap[header] = index
        }else {
            mHeaderList.add(header)
        }
    }

    override fun addFooter(footer: View) {
        val index = mRecyclerView?.addFooter(footer, true)
        if (index != null) {
            mHeaderFooterMap[footer] = index
        }else {
            mFooterList.add(footer)
        }

    }

    override fun showHeader(header: View, show: Boolean) {
        val viewType = mHeaderFooterMap[header]
        if (viewType != null) {
            mRecyclerView?.showHeader(viewType, show, true)
        }
    }

    override fun showFooter(footer: View, show: Boolean) {
        val viewType = mHeaderFooterMap[footer]
        if (viewType != null) {
            mRecyclerView?.showFooter(viewType, show, true)
        }
    }

    override fun setSelected(recipient: Recipient, select: Boolean) {
        mRecyclerView?.setSelected(recipient, select)
    }

    override fun setFixedSelected(recipientList: List<Recipient>) {
        recipientList.forEach {
            if (!mFixedSelectedList.contains(it)) {
                mFixedSelectedList.add(it)
            }
        }
        mRecyclerView?.setSelectedRecipient(recipientList, true)
    }

    override fun setContactSelectCallback(callback: IContactsCallback) {
        mCallback = callback
    }

}
