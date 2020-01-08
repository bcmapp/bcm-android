package com.bcm.messenger.contacts

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.event.GroupInfoCacheReadyEvent
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonSearchBar
import com.bcm.messenger.common.ui.RecipientAvatarView
import com.bcm.messenger.common.ui.Sidebar
import com.bcm.messenger.common.ui.StickyLinearDecoration
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.adapter.IListDataSource
import com.bcm.messenger.common.ui.adapter.ListDataSource
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.contacts.viewmodel.GroupContactViewModel
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.contacts_fragment_group.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Created by wjh on 2018/8/16.
 */
@Route(routePath = ARouterConstants.Fragment.CONTACTS_GROUP)
class GroupContactFragment : BaseFragment(), AmeRecycleViewAdapter.IViewHolderDelegate<GroupContactViewModel.GroupContactViewData> {

    companion object {
        private const val TAG = "GroupContactFragment"
        private const val SEARCH_BAR_VIEW_STYLE = 2
    }

    private val dataSource = object : ListDataSource<GroupContactViewModel.GroupContactViewData>() {
        override fun getItemId(position: Int): Long {
            return getData(position).groupInfo.gid
        }
    }

    private var groupContactViewModel: GroupContactViewModel? = null

    override fun onDestroy() {
        super.onDestroy()
        ALog.d(TAG, "onDestroy")
        groupContactViewModel?.destroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ALog.d(TAG, "onCreate")
        EventBus.getDefault().register(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        group_contact_list?.removeItemDecorationAt(0)
        group_contact_list?.removeAllViews()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.contacts_fragment_group, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        group_contact_list.layoutManager = LinearLayoutManager(context)
        val adapter = GroupContactAdapter(view.context, dataSource)
        adapter.setHasStableIds(true)
        group_contact_list.adapter = adapter
        adapter.setViewHolderDelegate(this)
        group_contact_list.addItemDecoration(StickyLinearDecoration(object : StickyLinearDecoration.StickyHeaderCallback {

            private val mHeaderMap: MutableMap<String, TextView> = mutableMapOf()

            override fun getHeaderData(pos: Int): StickyLinearDecoration.StickyHeaderData? {
                try {
                    if (pos < 0 || pos >= adapter.itemCount) {
                        return null
                    }
                    var pData: GroupContactViewModel.GroupContactViewData? = null
                    var nData: GroupContactViewModel.GroupContactViewData? = null
                    val cData = groupContactViewModel?.getGroupList()?.get(pos) ?: return null
                    if (!cData.isTrueData) {
                        return null
                    }
                    val pIndex = pos -1
                    if (pIndex >= 0) {
                        pData = groupContactViewModel?.getGroupList()?.get(pIndex)
                    }
                    val nIndex = pos + 1
                    if (nIndex < adapter.itemCount) {
                        nData = groupContactViewModel?.getGroupList()?.get(nIndex)
                    }
                    val isFirst = cData.firstLetter != pData?.firstLetter || (cData.isTrueData && !pData.isTrueData)
                    val isLast = cData.firstLetter != nData?.firstLetter || (cData.isTrueData && !nData.isTrueData)
                    var header = mHeaderMap[cData.firstLetter.toString()]
                    if (header == null) {
                        header = TextView(context)
                        header.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        header.text = cData.firstLetter.toString()
                        header.gravity = Gravity.CENTER_VERTICAL
                        header.setTextColor(getColorCompat(R.color.common_color_909090))
                        header.textSize = 15.0f
                        header.setBackgroundColor(Color.parseColor("#E5FFFFFF"))
                        val w = AppContextHolder.APP_CONTEXT.resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
                        val h = 8.dp2Px()
                        header.setPadding(w, h, w, h)

                        mHeaderMap[cData.firstLetter.toString()] = header
                    }
                    return StickyLinearDecoration.StickyHeaderData(isFirst, isLast, header, 36.dp2Px(), 1.dp2Px())

                }catch (ex: Exception) {
                    ALog.e(TAG, "getHeaderData error", ex)
                }
                return null
            }
        }))

        val viewModel = GroupContactViewModel(accountContext) {self, list ->
            ALog.d(TAG, "GroupContactViewModel update groupList: ${list.size}")
            if (self.getTrueDataSize() == 0) {
                group_contact_sidebar?.hide()
            }
            dataSource.updateDataSource(list)
        }

        viewModel.loadGroupList()

        this.groupContactViewModel = viewModel

        dataSource.updateDataSource(viewModel.getGroupList())

        group_contact_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            var isDragging = false
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isDragging = newState == RecyclerView.SCROLL_STATE_DRAGGING
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                try {
                    if (isDragging) {
                        return
                    }
                    val pos = (recyclerView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
                    val letter = groupContactViewModel?.getGroupList()?.get(pos)?.firstLetter?.toString()
                    group_contact_sidebar?.selectLetter(letter)

                }catch (ex: Exception) {
                    ALog.e(TAG, "onScrolled error")
                }
            }
        })

        group_contact_sidebar.setLetterList(Recipient.LETTERS.toList())
        group_contact_sidebar.setOnTouchingLetterChangedListener(object : Sidebar.OnTouchingLetterChangedListener {
            override fun onLetterChanged(letter: String): Int {
                val groupModel = groupContactViewModel ?: return -1
                var pos = 0
                for ((index, info) in groupModel.getGroupList().withIndex()) {
                    if (info.firstLetter.toString() == letter) {
                        pos = index
                        break
                    }
                }
                return pos
            }

            override fun onLetterScroll(position: Int) {
                if (position in 0 until viewModel.getGroupList().size) {
                    val layoutManager = group_contact_list.layoutManager as LinearLayoutManager
                    layoutManager.scrollToPositionWithOffset(position, 0)
                }
            }

        })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: GroupInfoCacheReadyEvent) {
        ALog.d(TAG, "receive GroupInfoCacheReadyEvent")
        groupContactViewModel?.loadGroupList()
    }

    override fun getViewHolderType(adapter: AmeRecycleViewAdapter<GroupContactViewModel.GroupContactViewData>, position: Int, data: GroupContactViewModel.GroupContactViewData): Int {
        return when(data){
            GroupContactViewModel.SEARCH_BAR -> SEARCH_BAR_VIEW_STYLE
            GroupContactViewModel.GROUP_EMPTY -> R.layout.contacts_layout_empty_group
            else -> R.layout.contacts_item_group
        }
    }

    override fun bindViewHolder(adapter: AmeRecycleViewAdapter<GroupContactViewModel.GroupContactViewData>, viewHolder: AmeRecycleViewAdapter.ViewHolder<GroupContactViewModel.GroupContactViewData>) {
        if (viewHolder is GroupHolder){
            val data = viewHolder.getData()
            if (null != data){
                viewHolder.bind(data)
            }
        }
    }

    override fun unbindViewHolder(adapter: AmeRecycleViewAdapter<GroupContactViewModel.GroupContactViewData>, viewHolder: AmeRecycleViewAdapter.ViewHolder<GroupContactViewModel.GroupContactViewData>) {
        if (viewHolder is GroupHolder) {
            viewHolder.unbind()
        }
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<GroupContactViewModel.GroupContactViewData>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<GroupContactViewModel.GroupContactViewData> {
        return when(viewType){
            SEARCH_BAR_VIEW_STYLE -> {
                SearchBarHolder(CommonSearchBar(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    val lr = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
                    val tb = resources.getDimensionPixelSize(R.dimen.common_vertical_gap)
                    setPadding(lr, tb, lr, tb)
                    setMode(CommonSearchBar.MODE_DISPLAY)
                    setOnSearchActionListener(object : CommonSearchBar.OnSearchActionListener{
                        override fun onJump() {
                            AmeModuleCenter.contact(accountContext)?.openSearch(context)
                        }

                        override fun onSearch(keyword: String) {
                        }

                        override fun onClear() {
                        }

                    })
                })
            }
            R.layout.contacts_layout_empty_group -> EmptyHolder(inflater.inflate(viewType, parent, false))
            else -> GroupHolder(inflater.inflate(viewType, parent, false))
        }
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<GroupContactViewModel.GroupContactViewData>, viewHolder: AmeRecycleViewAdapter.ViewHolder<GroupContactViewModel.GroupContactViewData>) {
        val data = viewHolder.getData()

        if (QuickOpCheck.getDefault().isQuick){
            return
        }

        if (data != null) {

            AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(HomeTopEvent(true,
                        HomeTopEvent.ConversationEvent.fromGroupConversation(null, data.groupInfo.gid)))

        }

    }

    class SearchBarHolder(val searchBar: CommonSearchBar) : AmeRecycleViewAdapter.ViewHolder<GroupContactViewModel.GroupContactViewData>(searchBar)
    class GroupContactAdapter<T : Any>(context: Context, dataModel: IListDataSource<T>) : AmeRecycleViewAdapter<T>(context, dataModel) {}
    class EmptyHolder(view: View) : AmeRecycleViewAdapter.ViewHolder<GroupContactViewModel.GroupContactViewData>(view)

    inner class GroupHolder(view: View) : AmeRecycleViewAdapter.ViewHolder<GroupContactViewModel.GroupContactViewData>(view) {
        val logoView: RecipientAvatarView = view.findViewById(R.id.group_logo_iv)
        val groupName: TextView = view.findViewById(R.id.group_name_tv)
        var gid = 0L

        fun bind(data: GroupContactViewModel.GroupContactViewData) {
            val groupInfo = data.groupInfo
            this.gid = groupInfo.gid
            this.logoView.showGroupAvatar(accountContext, groupInfo.gid)
            this.groupName.text = groupInfo.displayName
        }

        fun unbind() {
            ALog.i(TAG, "unbind")
            logoView.clear()
        }

    }
}
