package com.bcm.messenger.contacts.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonLoadingView
import com.bcm.messenger.common.ui.ConvenientRecyclerView
import com.bcm.messenger.common.ui.RecipientAvatarView
import com.bcm.messenger.common.ui.StickyLinearDecoration
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.contacts.R
import com.bcm.messenger.contacts.adapter.ContactsLinearDecorationCallback
import com.bcm.messenger.utility.Conversions
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import java.lang.ref.WeakReference
import java.security.MessageDigest

/**
 * Created by wjh on 2018/4/13
 */
class RecipientRecyclerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConvenientRecyclerView<Recipient>(context, attrs, defStyleAttr) {

    private val TAG = "RecipientRecyclerView"
    private var mListener: OnContactsActionListener? = null

    private var mMultiSelect: Boolean = false

    private val mSelectList: MutableSet<Recipient> = HashSet()

    private val mFixedSelectList: MutableSet<Recipient> = HashSet()

    private var mShowDecoration: Boolean = true

    private var mRegex: String? = null

    private var mDigest = MessageDigest.getInstance("SHA1")

    private var mShowBar = false

    private var enableChecker:SelectionEnableChecker.IChecker = SelectionEnableChecker.getChecker("")

    private var mCanChangeMode: Boolean = false

    init {
        addItemDecoration(StickyLinearDecoration(object : ContactsLinearDecorationCallback(context, mAdapter) {

            override fun getHeaderData(pos: Int): StickyLinearDecoration.StickyHeaderData? {
                return if (mShowDecoration) {
                    super.getHeaderData(pos)
                } else {
                    null
                }
            }
        }))
    }

    fun setShowSideBar(show: Boolean) {
        mShowBar = show
    }

    override fun showSideBar(): Boolean {
        return mShowBar && mAdapter.getTrueDataList().isNotEmpty()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getLetter(data: Recipient): String {
        return data.characterLetter
    }

    override fun onBindHeaderHolder(holder: LinearBaseAdapter.ViewHolder<Recipient>, position: Int) {

    }

    override fun onBindViewHolder(holder: LinearBaseAdapter.ViewHolder<Recipient>, trueData: Recipient?) {
        if(holder is ContactsViewHolder) {
            holder.bind()
        }
    }

    override fun onCreateDataHolder(parent: ViewGroup): ContactsViewHolder {
        return ContactsViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.contacts_item_contacts, parent, false))
    }

    override fun onViewRecycled(holder: LinearBaseAdapter.ViewHolder<Recipient>) {
        if(holder is ContactsViewHolder) {
            holder.unbind()
        }
    }

    override fun getItemId(allPosition: Int): Long {

        val type = getViewType(allPosition)
        val idText = if(type == LinearBaseAdapter.ITEM_TYPE_DATA) {
            mAdapter.getMainData(allPosition).data?.address?.serialize() ?: return 0
        } else {
            "id_individual_contact_extra_$type"
        }
        val bytes = mDigest.digest(idText.toByteArray())
        return Conversions.byteArrayToLong(bytes)
    }

    fun setDataList(dataList: List<Recipient>?, filter: String, showDecoration: Boolean) {
        mRegex = filter
        mShowDecoration = showDecoration
        setDataList(dataList)
    }

    fun setSelected(recipient: Recipient, select: Boolean) {

        val notify = if (select) {
            mSelectList.add(recipient)
        } else {
            mSelectList.remove(recipient)
        }
        if (notify) {
            mAdapter.notifyDataSetChanged()
            if (select) {
                mListener?.onSelected(recipient)
            } else {
                mListener?.onDeselected(recipient)
            }
        }

    }

    fun setCanChangeMode(can: Boolean) {
        mCanChangeMode = can
    }

    fun isCanChangeMode(): Boolean {
        return mCanChangeMode
    }

    fun setSelectedRecipient(recipients: List<Recipient>, fixed: Boolean = true) {
        if (fixed) {
            recipients.forEach {
                if (!mFixedSelectList.contains(it)) {
                    mFixedSelectList.add(it)
                }
                if (!mSelectList.contains(it)) {
                    mSelectList.add(it)
                }
            }

        } else {
            recipients.forEach {
                if (!mSelectList.contains(it)) {
                    mSelectList.add(it)
                }
            }
        }
        mAdapter.notifyDataSetChanged()
    }

    fun setShowDecoration(show: Boolean, notify: Boolean = false) {
        mShowDecoration = show
        if (notify) {
            mAdapter.notifyDataSetChanged()
        }
    }

    fun setMultiSelect(multiSelect: Boolean, notify: Boolean = false) {
        setMultiSelect(multiSelect, null, notify)
    }

    fun setMultiSelect(multiSelect: Boolean, currentSelect: Recipient? = null, notify: Boolean = false) {
        if (mMultiSelect != multiSelect) {
            ALog.i(TAG, "setMultiSelect multiSelect: $multiSelect, notify: $notify")
            mMultiSelect = multiSelect
            mSelectList.clear()
            mListener?.onModeChanged(multiSelect)
            if (currentSelect != null) {
                if (!mFixedSelectList.contains(currentSelect)) {
                    mSelectList.add(currentSelect)
                    mListener?.onSelected(currentSelect)
                }
            }
            if (notify) {
                mAdapter.notifyDataSetChanged()
            }
        }
    }

    fun setEnableChecker(enableChecker:SelectionEnableChecker.IChecker) {
        this.enableChecker = enableChecker
    }

    fun isMultiSelect(): Boolean {
        return mMultiSelect
    }

    fun setOnContactsActionListener(listener: OnContactsActionListener) {
        mListener = listener
    }

    inner class ContactsViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<Recipient>(itemView), RecipientModifiedListener {

        private val photoView: RecipientAvatarView = itemView.findViewById(R.id.contacts_logo_iv)
        private val nameView: TextView = itemView.findViewById(R.id.contacts_name_tv)
        private val selectView: ImageView = itemView.findViewById(R.id.contacts_select)
        private val selectingView: CommonLoadingView = itemView.findViewById(R.id.contacts_selecting)

        init {
            itemView.setOnClickListener {
                val r = this.data ?: return@setOnClickListener
                val weakSelf = WeakReference(this)
                val weakRecycler = WeakReference(this@RecipientRecyclerView)
                if (mMultiSelect) {
                    val state = enableChecker.checkState(r.address)
                    if (state == SelectionEnableChecker.STATE.DISABLE) {
                        AmeAppLifecycle.failure("This user version is too low to invite a group chat", true)
                        return@setOnClickListener
                    }

                    if (!selectView.isEnabled) {
                        return@setOnClickListener
                    }

                    enableChecker.canEnable(r.address, true)
                            .subscribe {
                                if (it == SelectionEnableChecker.STATE.CHECKING) {
                                    when {
                                        state == SelectionEnableChecker.STATE.CHECKING -> return@subscribe
                                        weakSelf.get()?.data?.address != r.address -> weakRecycler.get()?.notifyDataChanged()
                                        else -> weakSelf.get()?.showChecking()
                                    }
                                    return@subscribe
                                } else if (it == SelectionEnableChecker.STATE.DISABLE) {
                                    when {
                                        weakSelf.get()?.data?.address != r.address -> weakRecycler.get()?.notifyDataChanged()
                                        else -> weakSelf.get()?.disableSelection()
                                    }
                                    return@subscribe
                                }


                                val isChecked = if (mSelectList.contains(r)) {
                                    mSelectList.remove(r)
                                    mListener?.onDeselected(r)
                                    false
                                } else {
                                    mSelectList.add(r)
                                    mListener?.onSelected(r)
                                    true
                                }

                                if (weakSelf.get()?.data?.address != r.address) {
                                    weakRecycler.get()?.notifyDataChanged()
                                } else {
                                    changeSelectView(selectView, isChecked)
                                }
                            }
                } else {
                    mListener?.onSelected(r)
                }
            }
            itemView.setOnLongClickListener {
                ALog.i(TAG, "onLongClick canChangeMode: $mCanChangeMode")
                if (mCanChangeMode && !mMultiSelect) {
                    setMultiSelect(!mMultiSelect, this.data, true)
                }
                mCanChangeMode && !mMultiSelect
            }
        }

        fun unbind() {
            this.data?.removeListener(this)
        }

        fun bind() {
            val recipient = this.data ?: return
            recipient.addListener(this)

            val weakSelf = WeakReference(this)
            val weakRecyclerView = WeakReference(this@RecipientRecyclerView)
            enableChecker.canEnable(recipient.address, false)
                    .subscribe {
                        val selectView = weakSelf.get()?.selectView?:return@subscribe
                        val photoView = weakSelf.get()?.photoView?:return@subscribe
                        val nameView = weakSelf.get()?.nameView?:return@subscribe
                        val selectingView = weakSelf.get()?.selectingView?:return@subscribe

                        val multiSelect = weakRecyclerView.get()?.mMultiSelect?:return@subscribe
                        val fixedSelectList = weakRecyclerView.get()?.mFixedSelectList?:return@subscribe
                        val selectList = weakRecyclerView.get()?.mSelectList?:return@subscribe
                        val regex = weakRecyclerView.get()?.mRegex

                        selectView.visibility = if (multiSelect) {
                            selectView.isEnabled = (!fixedSelectList.contains(recipient))
                            val isChecked = selectList.contains(recipient)
                            weakRecyclerView.get()?.changeSelectView(selectView, isChecked)

                            when(it) {
                                SelectionEnableChecker.STATE.CHECKING -> {
                                    weakSelf.get()?.showChecking()
                                }
                                else -> {
                                    weakSelf.get()?.hideChecking()
                                }
                            }

                            if (it == SelectionEnableChecker.STATE.DISABLE) {
                                disableSelection()
                            } else {
                                enableSelection()
                            }

                            View.VISIBLE
                        } else {
                            selectingView.stopAnim()
                            selectingView.visibility = View.GONE

                            View.GONE
                        }

                        photoView.showRecipientAvatar(recipient)
                        val name = recipient.name
                        nameView.text = if (regex.isNullOrEmpty()) {
                            name
                        } else {
                            StringAppearanceUtil.applyFilterAppearanceIgnoreCase(name, regex, color = getColor(R.color.contacts_filter_color))
                        }
                    }
        }

        private fun disableSelection() {
            itemView.alpha = 0.3f
            hideChecking()
        }

        private fun enableSelection() {
            itemView.alpha = 1.0f
        }

        private fun showChecking() {
            selectingView.visibility = View.VISIBLE
            if (!selectingView.isStarted()) {
                selectingView.startAnim()
            }
        }

        private fun hideChecking() {
            selectingView.stopAnim()
            selectingView.visibility = View.GONE
        }

        override fun onModified(recipient: Recipient) {
            if(this.data == recipient) {
                itemView.post {
                    bind()
                }
            }
        }
    }

    private fun changeSelectView(selectView: ImageView, isChecked: Boolean) {
        if (!selectView.isEnabled) {
            selectView.setImageResource(R.drawable.common_checkbox_selected_grey)
        } else if (isChecked) {
            selectView.setImageResource(R.drawable.common_checkbox_selected)
        } else {
            selectView.setImageResource(R.drawable.common_checkbox_unselected)
        }
    }

    interface OnContactsActionListener {
        fun onDeselected(recipient: Recipient)
        fun onSelected(recipient: Recipient)
        fun onModeChanged(multiSelect: Boolean) {}
    }
}