package com.bcm.messenger.chats.forward

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ui.CustomDataSearcher
import kotlinx.android.synthetic.main.chats_forward_recent_header.view.*
import com.bcm.messenger.common.recipients.Recipient

/**
 * Header for forward activity
 */
class ForwardHeaderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    interface ForwardHeaderViewCallback {
        fun onSearchResult(filter: String, result: List<Recipient>)
        fun onContactsClicked()
        fun onGroupClicked()
    }

    private var callback: ForwardHeaderViewCallback? = null
    private val searchBar: CustomDataSearcher<Recipient>
    private var searchList = listOf<Recipient>()

    init {
        inflate(context, R.layout.chats_forward_recent_header, this)

        searchBar = findViewById(R.id.recent_search_bar)
        searchBar.setOnSearchActionListener(object : CustomDataSearcher.OnSearchActionListener<Recipient>() {
            override fun onSearchResult(filter: String, results: List<Recipient>) {
                recent_title.visibility = View.GONE
                callback?.onSearchResult(filter, results)
            }

            override fun onSearchNull(results: List<Recipient>) {
                recent_title.visibility = View.VISIBLE
                callback?.onSearchResult("", results)
            }

            override fun onMatch(data: Recipient, compare: String): Boolean {
                return Recipient.match(compare, data)
            }
        })
        searchBar.setSourceList(searchList)

        recent_contacts.setOnClickListener {
            callback?.onContactsClicked()
        }
        recent_groups.setOnClickListener {
            callback?.onGroupClicked()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        layoutParams = layoutParams.apply {
            width = LayoutParams.MATCH_PARENT
        }
    }

    fun setCallback(callback: ForwardHeaderViewCallback) {
        this.callback = callback
    }

    fun setSearchSource(list: List<Recipient>) {
        searchBar.setSourceList(list)
    }
}