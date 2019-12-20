package com.bcm.messenger.contacts.adapter

import android.content.Context
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonSearchBar
import com.bcm.messenger.common.ui.ContentShadeView
import com.bcm.messenger.common.ui.RecipientAvatarView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.contacts.R
import com.bcm.messenger.utility.Conversions
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import kotlinx.android.synthetic.main.contacts_header_individual.view.*
import java.security.MessageDigest

/**
 * Created by wjh on 2018/3/8
 */
class IndividualContactAdapter(context: Context, val mListener: OnContactActionListener?) : LinearBaseAdapter<Recipient>(context){

    private val TAG = "IndividualContactAdapter"

    private var mDigest = MessageDigest.getInstance("SHA1")
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var mShowLoading = false
    private var mHeaderSearch: Int = 0
    private var mHeaderTool: Int = 0
    private var mHeaderShade: Int = 0

    init {
        setHasStableIds(true)
        mShowLoading = true

        mHeaderSearch = addHeader()
        mHeaderTool = addHeader()
        mHeaderShade = addHeader()

        notifyMainChanged()
    }

    fun showLoading(loading: Boolean) {
        mShowLoading = loading
        notifyDataSetChanged()
    }

    override fun showSideBar(): Boolean {
        return getTrueDataList().isNotEmpty()
    }

    override fun findSidePosition(letter: String): Int {
        var current: BaseLinearData<Recipient>? = null
        for (i in 0 until itemCount) {
            current = getMainData(i)
            if (current.letter == letter) {
                return findPreviousHeaderPosition(i)
            }
        }
        return -1
    }

    private fun findPreviousHeaderPosition(position: Int): Int {
        val p = position - 1
        val previous: BaseLinearData<Recipient>
        return if (p in 0 until itemCount) {
            previous = getMainData(p)
            if (previous.type != ITEM_TYPE_DATA) {
                findPreviousHeaderPosition(p)
            }else {
                position
            }
        }else {
            position
        }

    }


    override fun findSideLetter(position: Int): String? {
        return findAppropriateLetter(position)
    }

    private fun findAppropriateLetter(position: Int): String? {
        return if (position in 0 until itemCount) {
            val data = getMainData(position)
            if (data.type == ITEM_TYPE_DATA) {
                data.letter
            }else {
                findAppropriateLetter(position + 1)
            }
        }else {
            ""
        }
    }

    override fun getLetter(data: Recipient): String {
        return data.characterLetter
    }

    override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<Recipient> {
        return ContactsViewHolder(inflater.inflate(R.layout.contacts_item_contacts, parent, false))
    }

    override fun onCreateHeaderHolder(parent: ViewGroup, viewType: Int): ViewHolder<Recipient> {
        return when(viewType) {
            mHeaderSearch -> {
                val searchbar = CommonSearchBar(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    val lr = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
                    val tb = resources.getDimensionPixelSize(R.dimen.common_vertical_gap)
                    setPadding(lr, tb, lr, tb)
                    setMode(CommonSearchBar.MODE_DISPLAY)
                    setOnSearchActionListener(object : CommonSearchBar.OnSearchActionListener{
                        override fun onJump() {
                            mListener?.onSearch()
                        }

                        override fun onSearch(keyword: String) {
                        }

                        override fun onClear() {
                        }

                    })
                }
                SearchBarHolder(searchbar)
            }
            mHeaderTool -> {
                val v = inflater.inflate(R.layout.contacts_header_individual, parent, false)
                ToolHolder(v)
            }
            mHeaderShade -> {
                val v = ContentShadeView(parent.context)
                v.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                v.setPadding(0, 70.dp2Px(), 0, 50.dp2Px())
                ShadeViewHolder(v)
            } else -> {
                super.onCreateHeaderHolder(parent, viewType)
            }
        }
    }

    override fun onBindHeaderHolder(holder: ViewHolder<Recipient>, position: Int) {
        super.onBindHeaderHolder(holder, position)
        if (holder is ShadeViewHolder) {
            holder.bind(mShowLoading)
        }else if (holder is SearchBarHolder) {
            holder.bind()
        }
    }

    override fun onBindContentHolder(holder: ViewHolder<Recipient>, trueData: Recipient?) {

        if (holder is ContactsViewHolder) {
            holder.bind()
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder<Recipient>) {

    }

    fun setContacts(friendList: List<Recipient>) {
        mShowLoading = false
        showHeader(mHeaderShade, friendList.isEmpty(), false)
        setDataList(friendList)
    }

    override fun getItemId(position: Int): Long {
        val type = getItemViewType(position)
        val idText = if(type == ITEM_TYPE_DATA) {
            getMainData(position).data?.address?.serialize() ?: return 0
        } else {
            "id_individual_contact_extra_$type"
        }

        val bytes = mDigest.digest(idText.toByteArray())
        return Conversions.byteArrayToLong(bytes)
    }

    override fun onViewRecycled(holder: ViewHolder<Recipient>) {
        if(holder is ContactsViewHolder) {
            holder.unbind()
        }
    }

    inner class ShadeViewHolder(private val shadeView: ContentShadeView) : ViewHolder<Recipient>(shadeView) {
        init {
            shadeView.setOnContentClickListener { isLoadingStatus ->
                if (!isLoadingStatus) {
                    mListener?.onEmpty()
                }
            }
        }

        fun bind(isLoading: Boolean) {
            if (isLoading) {
                shadeView.showLoading()
            } else {
                val context = shadeView.context
                val builder = SpannableStringBuilder(StringAppearanceUtil.applyAppearance(context.getString(R.string.contacts_empty_title_text), shadeView.getTitleSize(), shadeView.getTitleColor()))
                shadeView.showContent(builder)
            }
        }
    }

    inner class ToolHolder(itemView: View) : ViewHolder<Recipient>(itemView) {


        init {
            itemView.header_group_layout.setOnClickListener {
                if (QuickOpCheck.getDefault().isQuick) {
                    return@setOnClickListener
                }
                mListener?.onGroupContact()
            }
        }
    }

    inner class SearchBarHolder(private val searchbar: CommonSearchBar) : ViewHolder<Recipient>(searchbar) {
        fun bind() {

        }
    }

    inner class ContactsViewHolder(itemView: View) : ViewHolder<Recipient>(itemView) {

        private val photoView = itemView.findViewById<RecipientAvatarView>(R.id.contacts_logo_iv)
        private val nameView = itemView.findViewById<TextView>(R.id.contacts_name_tv)

        init {
            itemView.setOnClickListener {
                if (QuickOpCheck.getDefault().isQuick) {
                    return@setOnClickListener
                }
                mListener?.onSelect(data?: return@setOnClickListener)
            }
        }

        fun unbind() {
        }

        fun bind() {
            val recipient = data ?: return
            this.nameView.setTextColor(getColor(R.color.common_color_black))

            this.nameView.text = recipient.name
            this.photoView.showPrivateAvatar(recipient)
        }

    }

    interface OnContactActionListener {
        fun onSelect(recipient: Recipient)
        fun onSearch()
        fun onEmpty()
        fun onGroupContact()
    }
}

