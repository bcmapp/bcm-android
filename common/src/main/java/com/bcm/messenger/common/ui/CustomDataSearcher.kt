package com.bcm.messenger.common.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.R
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.common_search_bar.view.*
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.common.utils.showKeyboard
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * 
 * Created by wjh on 2018/4/10
 */
open class CustomDataSearcher<T> : ConstraintLayout, TextView.OnEditorActionListener {

    private val TAG = "CustomDataSearcher"

    private var mSourceList: List<T>? = null
    private var mListener: OnSearchActionListener<T>? = null
    private var mSearchDispose: Disposable? = null
    private var mIMESearchable: Boolean = false

    private var mTextWatcher: TextWatcher? = null

    private var mLastSearchText: String? = null//
    private var mLastSearchList: List<T>? = null//

    private var mHasSearchChanged = false
        @Synchronized set
        @Synchronized get

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {

        View.inflate(context, R.layout.common_search_bar, this)
        common_search_close.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            common_search_text.setText("")
        }
        common_search_text.setOnClickListener {
            common_search_text.isFocusable = true
            common_search_text.isFocusableInTouchMode = true
            requestSearchFocus()
            common_search_text.post {
                showKeyboard()
            }
        }
        common_search_text.setOnEditorActionListener(this)

        showTip(false)
        val paddingLeftRight = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
        val paddingTopDown = resources.getDimensionPixelSize(R.dimen.common_vertical_gap)
        if (paddingStart == 0 && paddingEnd == 0 && paddingTop == 0 && paddingBottom == 0) {
            super.setPadding(paddingLeftRight, paddingTopDown, paddingLeftRight, paddingTopDown)
        }

        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        enableIMESearch(false)

        post {
            createSearchObservable()
        }
    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        return if (mIMESearchable && actionId == EditorInfo.IME_ACTION_SEARCH) {
            mListener?.onSearchClick(common_search_text.text.toString())
            true
        }else {
            false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        createSearchObservable()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroySearchObservable()
    }

    /**
     * 
     */
    fun recycle() {
        hideSearching()
        mSearchDispose?.dispose()
        mSourceList = null
        mLastSearchText = null
        mLastSearchList = null
    }

    /**
     * 
     */
    fun enableIMESearch(enable: Boolean) {
        mIMESearchable = enable
        if (enable) {
            common_search_text?.imeOptions = EditorInfo.IME_ACTION_SEARCH
        }else {
            common_search_text?.imeOptions = EditorInfo.IME_ACTION_NONE
        }
    }

    /**
     * 
     */
    fun requestSearchFocus() {
        common_search_text.requestFocus()
        common_search_text.setSelection(common_search_text.text.length)
    }

    fun showKeyboard() {
        common_search_text?.showKeyboard()
    }

    /**
     * 
     */
    fun getSearchText(): CharSequence {
        return common_search_text.text.toString()
    }

    /**
     * 
     */
    fun setSearchText(searchText: CharSequence) {
        post {
            if (searchText != common_search_text.text.toString()) {
                common_search_text.setText(searchText)
            }
        }

    }

    /**
     * 
     */
    fun setTipAppearance(textSize: Int, textColor: Int) {
        common_search_tips.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize.toFloat())
        common_search_tips.setTextColor(textColor)
    }

    /**
     * 
     */
    fun setSearchAppearance(textSize: Int, textColor: Int) {
        common_search_text.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize.toFloat())
        common_search_text.setTextColor(textColor)
    }

    /**
     * 
     */
    fun setSearchHintColor(color: Int) {
        common_search_text.setHintTextColor(color)
    }

    /**
     * 
     */
    fun showTip(show: Boolean) {
        common_search_tips.visibility = if (show) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    /**
     * 
     */
    fun hideTip() {
        common_search_tips.visibility = View.GONE
    }

    /**
     * 
     */
    fun setSearchTip(tip: CharSequence) {
        common_search_tips.text = tip
    }

    /**
     * 
     */
    fun setSearchHint(hint: CharSequence) {
        common_search_text.hint = hint
    }

    /**
     * 
     */
    fun setOnSearchActionListener(listener: OnSearchActionListener<T>?) {
        mListener = listener
    }

    /**
     * 
     */
    fun setSourceList(dataList: List<T>?) {
        mSourceList = dataList
    }

    /**
     * 
     */
    fun getSourceList(): List<T>? {
        return mSourceList
    }

    /**
     * 
     */
    private fun showSearching() {
        common_searching_iv?.post {
            common_searching_iv?.visibility = View.VISIBLE
            common_searching_iv?.startAnim()
        }
    }

    /**
     * 
     */
    private fun hideSearching() {
        common_searching_iv?.post {
            common_searching_iv?.visibility = View.GONE
            common_searching_iv?.stopAnim()
        }
    }

    /**
     * 
     */
    private fun createSearchObservable() {
        ALog.i(TAG, "createSearchObservable")
        if (mTextWatcher == null) {
            mTextWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    ALog.d(TAG, "afterTextChanged text: $s")
                    if (s.isNotEmpty()) {
                        common_search_close.visibility = View.VISIBLE
                    } else {
                        common_search_close.visibility = View.GONE
                    }

                    val searchText = s.toString()
                    hideSearching() //
                    mHasSearchChanged = true //
                    mSearchDispose?.dispose() //
                    mSearchDispose = Observable.just(1).delaySubscription(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                            .subscribe({
                                handleSearchAction(searchText)
                            }, {

                            })

                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    ALog.d(TAG, "onTextChanged text: $s")
                }

            }
            common_search_text?.addTextChangedListener(mTextWatcher)
        }

    }

    /**
     * 
     */
    private fun handleSearchAction(searchText: String) {
        ALog.i(TAG, "handleSearchAction searchText: $searchText")
        if (searchText == getSearchText()) { //
            if (searchText.isEmpty()) {
                post {
                    hideSearching()
                    mListener?.onSearchNull(mSourceList ?: listOf())
                    requestSearchFocus()
                }

            }else {

                val resultList = mutableListOf<T>()
                val listener = mListener
                if (listener != null) {
                    try {
                        mHasSearchChanged = false //
                        showSearching() //
                        //
                        val searchList = if (!mLastSearchText.isNullOrEmpty() && searchText.startsWith(mLastSearchText ?: "")) {
                            mLastSearchList ?: mSourceList?.toList() ?: listOf()
                        }else {
                            mSourceList?.toList() ?: listOf()
                        }
                        var partCount = 0
                        var threadCount = 1
                        if (searchList.size > 6000) {
                            threadCount = 4
                        }else if (searchList.size > 3000) {
                            threadCount = 3
                        }else if (searchList.size > 1500) {
                            threadCount = 2
                        }
                        partCount = searchList.size / threadCount

                        ALog.i(TAG, "handleSearchAction threadCount: $threadCount, partCount: $partCount")
                        val jobArray = mutableListOf<Deferred<List<T>>>()
                        for (i in 0 until threadCount) {
                            val start = i * partCount
                            ALog.i(TAG, "handleSearchAction i: $i, start: $start")
                            jobArray.add(GlobalScope.async(Dispatchers.IO) {
                                val temp = mutableListOf<T>()
                                val end = if (i < (threadCount - 1)) {
                                    i + partCount
                                } else {
                                    searchList.size
                                }
                                ALog.i(TAG, "handleSearchAction job, start: $start, end: $end")
                                for (n in start until end) {
                                    if (mHasSearchChanged) { //
                                        break
                                    }
                                    if (listener.onMatch(searchList[n], searchText)) {
                                        temp.add(searchList[n])
                                    }
                                }
                                temp
                            })
                        }

                        GlobalScope.launch(Dispatchers.Main) {
                            for (i in 0 until threadCount) {
                                resultList.addAll(jobArray[i].await())
                            }
                            ALog.i(TAG, "handleSearchAction finish search")
                            if (searchText == getSearchText()) { //
                                mLastSearchText = searchText //
                                mLastSearchList = resultList
                                hideSearching() //
                                listener.onSearchResult(searchText, resultList)
                                requestSearchFocus()
                            }
                        }

                    }catch (ex: Exception) {
                        ALog.e(TAG, "handleSearchAction error", ex)
                    }

                }
            }

        } else {
            ALog.i(TAG, "handleSearchAction cancel, searchText is not same: $searchText")
        }
    }

    /**
     * 
     */
    private fun destroySearchObservable() {
        ALog.i(TAG, "destroySearchObservable")
        if (mTextWatcher != null) {
            common_search_text?.removeTextChangedListener(mTextWatcher)
            mTextWatcher = null
        }
    }

    /**
     * 
     */
    abstract class OnSearchActionListener<in T> {
        abstract fun onSearchResult(filter: String, results: List<T>)    //
        abstract fun onSearchNull(results: List<T>)   //
        abstract fun onMatch(data: T, compare: String): Boolean  //
        open fun onSearchClick(searchText: String) {//

        }
    }

}