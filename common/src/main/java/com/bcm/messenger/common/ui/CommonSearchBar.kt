package com.bcm.messenger.common.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.R
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.common_search_bar_new.view.*
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.QuickOpCheck
import java.util.concurrent.TimeUnit

/**
 * 搜索栏组件
 * Created by wjh on 2018/4/10
 */
class CommonSearchBar : ConstraintLayout {

    companion object {
        private const val TAG = "CommonSearchBar"
        const val MODE_EDIT = 0
        const val MODE_DISPLAY = 1
    }

    private var mSearchDispose: Disposable? = null
    private var mListener: OnSearchActionListener? = null
    private var mTextWatcher: TextWatcher? = null

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {

        LayoutInflater.from(context).inflate(R.layout.common_search_bar_new, this)

        val attr = context.obtainStyledAttributes(attributeSet, R.styleable.CommonSearchBarStyle)
        val mode = attr.getInt(R.styleable.CommonSearchBarStyle_common_show_mode, MODE_EDIT)
        attr.recycle()

        setMode(mode)

        search_clear_iv.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            search_content_et.setText("")
        }

        common_search_display_layout.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            mListener?.onJump()
        }

        post {
            createSearchObservable()
        }

    }


    /**
     * 请求焦点
     */
    fun requestSearchFocus() {
        search_content_et.isFocusable = true
        search_content_et.requestFocus()
        search_content_et.setSelection(search_content_et.text.length)
    }

    /**
     * 设置搜索模式
     */
    fun setMode(mode: Int) {
        when(mode) {
            MODE_DISPLAY -> {
                common_search_display_layout.visibility = View.VISIBLE
                common_search_edit_layout.visibility = View.GONE
                isFocusable = false
                isFocusableInTouchMode = false
            }
            MODE_EDIT -> {
                common_search_display_layout.visibility = View.GONE
                common_search_edit_layout.visibility = View.VISIBLE
                isFocusable = true
                isFocusableInTouchMode = true
            }
        }
    }


    /**
     * 获取当前的索索字段
     */
    fun getSearchText(): CharSequence {
        return search_content_et.text.toString()
    }

    /**
     * 设置搜索字段
     */
    fun setSearchText(searchText: CharSequence) {
        post {
            if (searchText != search_content_et.text.toString()) {
                search_content_et.setText(searchText)
            }
        }
    }

    /**
     * 设置搜索hint
     */
    fun setSearchHint(hint: CharSequence) {
        search_content_et.hint = hint
    }

    /**
     * 设置搜索栏回调
     */
    fun setOnSearchActionListener(listener: OnSearchActionListener?) {
        mListener = listener
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ALog.i(TAG, "onAttachedToWindow")
        createSearchObservable()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ALog.i(TAG, "onDetachedFromWindow")
        destroySearchObservable()
    }

    /**
     * 创建搜索栏的订阅
     */
    private fun createSearchObservable() {

        if (mTextWatcher == null) {
            mTextWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    ALog.d(TAG, "afterTextChanged text: $s")
                    if (s.isNotEmpty()) {
                        search_clear_iv.visibility = View.VISIBLE
                    } else {
                        search_clear_iv.visibility = View.GONE
                    }
                    val searchText = s.toString()
                    mSearchDispose?.dispose()
                    mSearchDispose = Observable.just(1).delaySubscription(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                            .subscribe({
                                if (searchText == getSearchText()) {
                                    if (searchText.isEmpty()) {
                                        mListener?.onClear()
                                    }else {
                                        mListener?.onSearch(searchText)
                                    }
                                }else {
                                    ALog.i(TAG, "receive searchTextChangedObservable: $searchText not same to current")
                                }
                            }, {

                            })
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    ALog.d(TAG, "onTextChanged text: $s")
                }

            }
            search_content_et?.addTextChangedListener(mTextWatcher)
        }
    }

    /**
     * 取消搜索栏的订阅
     */
    private fun destroySearchObservable() {
        ALog.d(TAG, "destroySearchObservable")
        mSearchDispose?.dispose()
        if (mTextWatcher != null) {
            search_content_et?.removeTextChangedListener(mTextWatcher)
            mTextWatcher = null
        }
    }

    /**
     * 搜索栏输入行为回调
     */
    interface OnSearchActionListener {

        fun onJump()//跳转
        fun onSearch(keyword: String)//关键字变更
        fun onClear()//搜索栏清空
    }

}