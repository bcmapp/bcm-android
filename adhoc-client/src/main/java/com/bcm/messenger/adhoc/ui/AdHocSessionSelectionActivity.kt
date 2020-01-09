package com.bcm.messenger.adhoc.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.component.AdHocSessionAvatar
import com.bcm.messenger.adhoc.logic.AdHocSession
import com.bcm.messenger.adhoc.logic.AdHocSessionLogic
import com.bcm.messenger.chats.components.recyclerview.SelectionDataSource
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.CustomDataSearcher
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.StringAppearanceUtil
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.adhoc_session_selection_activity.*
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.ui.emoji.EmojiTextView

/**
 * adhoc session select activity
 */
class AdHocSessionSelectionActivity: AccountSwipeBaseActivity(),AmeRecycleViewAdapter.IViewHolderDelegate<AdHocSession> {

    private val TAG = "AdHocSessionSelectionActivity"
    private var mMultiSelectMode: Boolean = false
    private val dataSource = object : SelectionDataSource<AdHocSession>() {

        override fun getItemId(position: Int): Long {
            return EncryptUtils.byteArrayToLong(getData(position).sessionId.toByteArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, com.bcm.messenger.chats.R.anim.common_slide_from_bottom_fast)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, com.bcm.messenger.chats.R.anim.common_slide_to_bottom_fast)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.adhoc_session_selection_activity)
        adhoc_session_selection_toolbar.setListener(object :CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                val list = dataSource.selectList()
                if (list.isNotEmpty()) {
                    val intent = Intent().apply {
                        putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, list[0].sessionId)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
            }
        })

        adhoc_session_searchbar.setOnSearchActionListener(object : CustomDataSearcher.OnSearchActionListener<AdHocSession>() {
            override fun onSearchResult(filter: String, results: List<AdHocSession>) {
                dataSource.updateDataSource(results)
            }

            override fun onSearchNull(results: List<AdHocSession>) {
                dataSource.updateDataSource(results)
            }

            override fun onMatch(data: AdHocSession, compare: String): Boolean {
                val name = data.displayName(accountContext)
                return StringAppearanceUtil.containIgnore(name, compare)
            }
        })

        val title = intent.getStringExtra(ARouterConstants.PARAM.ADHOC.SESSION_TITLE) ?: getString(R.string.adhoc_session_selection_title)
        adhoc_session_selection_toolbar.setCenterText(title)

        mMultiSelectMode = intent.getBooleanExtra(ARouterConstants.PARAM.ADHOC.SESSION_MULTISELECT, false)
        val exceptSessionId = intent.getStringExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION) ?: ""

        adhoc_session_selection_list.layoutManager = LinearLayoutManager(this)
        val adapter = AmeRecycleViewAdapter(this,dataSource)
        adapter.setViewHolderDelegate(this)
        adapter.setHasStableIds(true)
        adhoc_session_selection_list.adapter = adapter

        if (mMultiSelectMode) {
            adhoc_session_selection_toolbar.getRightView().second?.visibility = View.VISIBLE
        }else {
            adhoc_session_selection_toolbar.getRightView().second?.visibility = View.GONE
        }

        Observable.create<List<AdHocSession>> {
            val list = AdHocSessionLogic.get(accountContext).getSessionList().filter { it.sessionId != exceptSessionId }
            it.onNext(list)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    dataSource.updateDataSource(it)
                    adhoc_session_searchbar?.setSourceList(it)
                }, {
                    ALog.e(TAG, "getAdHocSessionList fail", it)
                })
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<AdHocSession>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<AdHocSession> {
        val view = inflater.inflate(R.layout.adhoc_session_selection_item, parent, false)
        return SessionHolder(view)
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<AdHocSession>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AdHocSession>) {
        super.onViewClicked(adapter, viewHolder)
        val holder = viewHolder as SessionHolder

        if (mMultiSelectMode) {
            if (dataSource.selectList().isNotEmpty()) {
                val data = dataSource.selectList()[0]
                if (data == holder.getData()) {
                    return
                }

                dataSource.unSelect(data)
            }
            dataSource.select(holder.getData() ?: return)

        }else {
            val intent = Intent().apply {
                putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, holder.getData()?.sessionId)
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    inner class SessionHolder(view: View):AmeRecycleViewAdapter.ViewHolder<AdHocSession>(view) {
        private val selection = view.findViewById<ImageView>(R.id.session_select)
        private val avatar = view.findViewById<AdHocSessionAvatar>(R.id.session_select_avatar)
        private val name = view.findViewById<EmojiTextView>(R.id.session_name_tv)
        override fun setData(data: AdHocSession) {
            super.setData(data)
            avatar.setSession(accountContext, data)
            name.text = data.displayName(accountContext)
            if (mMultiSelectMode) {
                selection.visibility = View.VISIBLE
                if (dataSource.isSelected(data)) {
                    selection.setImageResource(R.drawable.common_checkbox_selected)
                } else {
                    selection.setImageResource(R.drawable.common_checkbox_unselected)
                }
            }else {
                selection.visibility = View.GONE
            }
        }
    }
}