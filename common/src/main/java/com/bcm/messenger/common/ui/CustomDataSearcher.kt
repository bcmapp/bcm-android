package com.bcm.messenger.common.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
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
 * 搜索栏组件
 * Created by wjh on 2018/4/10
 */
open class CustomDataSearcher<T> : ConstraintLayout, TextView.OnEditorActionListener {

    private val TAG = "CustomDataSearcher"

    private var mSourceList: List<T>? = null
    private var mListener: OnSearchActionListener<T>? = null
    private var mSearchDispose: Disposable? = null
    private var mIMESearchable: Boolean = false

    private var mTextWatcher: TextWatcher? = null

    private var mLastSearchText: String? = null//上次的搜索文本
    private var mLastSearchList: List<T>? = null//上次的搜索结果（用于加大搜索精度的时候提高搜索效率）

    private var mSearchingAnim: ObjectAnimator? = null

    private var mHasSearchChanged = false
        @Synchronized set
        @Synchronized get

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}

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
        mSearchingAnim = ObjectAnimator.ofFloat(common_searching_iv, "rotation", 360f, 0f).apply {
            duration = 1500
            repeatCount = Animation.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator(context, attributeSet)
        }

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
     * 回收资源
     */
    fun recycle() {
        hideSearching()
        mSearchDispose?.dispose()
        mSourceList = null
        mLastSearchText = null
        mLastSearchList = null
    }

    /**
     * 是否激活输入面板的检索按钮
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
     * 请求搜索栏的焦点
     */
    fun requestSearchFocus() {
        common_search_text.requestFocus()
        common_search_text.setSelection(common_search_text.text.length)
    }

    fun showKeyboard() {
        common_search_text?.showKeyboard()
    }

    /**
     * 获取当前的索索字段
     */
    fun getSearchText(): CharSequence {
        return common_search_text.text.toString()
    }

    /**
     * 设置搜索字段
     */
    fun setSearchText(searchText: CharSequence) {
        post {
            if (searchText != common_search_text.text.toString()) {
                common_search_text.setText(searchText)
            }
        }

    }

    /**
     * 设置提示的样式
     */
    fun setTipAppearance(textSize: Int, textColor: Int) {
        common_search_tips.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize.toFloat())
        common_search_tips.setTextColor(textColor)
    }

    /**
     * 设置搜索栏的样式
     */
    fun setSearchAppearance(textSize: Int, textColor: Int) {
        common_search_text.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize.toFloat())
        common_search_text.setTextColor(textColor)
    }

    /**
     * 设置搜索栏提示语颜色
     */
    fun setSearchHintColor(color: Int) {
        common_search_text.setHintTextColor(color)
    }

    /**
     * 设置是否展示提示，默认不展示
     */
    fun showTip(show: Boolean) {
        common_search_tips.visibility = if (show) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    /**
     * 隐藏Tip布局
     */
    fun hideTip() {
        common_search_tips.visibility = View.GONE
    }

    /**
     * 设置搜索tip
     */
    fun setSearchTip(tip: CharSequence) {
        common_search_tips.text = tip
    }

    /**
     * 设置搜索hint
     */
    fun setSearchHint(hint: CharSequence) {
        common_search_text.hint = hint
    }

    /**
     * 设置搜索栏回调
     */
    fun setOnSearchActionListener(listener: OnSearchActionListener<T>?) {
        mListener = listener
    }

    /**
     * 设置要过滤的数据列表
     */
    fun setSourceList(dataList: List<T>?) {
        mSourceList = dataList
    }

    /**
     * 读取原本要过滤的数据列表
     */
    fun getSourceList(): List<T>? {
        return mSourceList
    }

    /**
     * 展示搜索中图标
     */
    private fun showSearching() {
        common_searching_iv?.post {
            common_searching_iv?.visibility = View.VISIBLE
            mSearchingAnim?.start()
        }
    }

    /**
     * 隐藏搜索中图标
     */
    private fun hideSearching() {
        common_searching_iv?.post {
            common_searching_iv?.visibility = View.GONE
            mSearchingAnim?.cancel()
        }
    }

    /**
     * 创建搜索栏的订阅
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
                    hideSearching() //触发新的搜索条件，先隐藏loading
                    mHasSearchChanged = true //设置属性为已经变更
                    mSearchDispose?.dispose() //把之前等待的先停掉，展开新的搜索任务
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
     * 处理检索行为
     */
    private fun handleSearchAction(searchText: String) {
        ALog.i(TAG, "handleSearchAction searchText: $searchText")
        if (searchText == getSearchText()) { //如果搜索的文本已经不一样，就没必要开展搜索任务
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
                        mHasSearchChanged = false //开始新的搜索，属性要重置
                        showSearching() //搜索开始，展示loading
                        //如果sourceList数量很大的时候，检索有点慢，为了加快速度，这里采用分线程的来提高速度
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
                                    if (mHasSearchChanged) { //如果搜索条件已经变化，则直接break，尽快释放线程
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
                            if (searchText == getSearchText()) { //如果最终的搜索文本已经不一样，则结果丢弃不能返回
                                mLastSearchText = searchText //把结果设置到最近的搜索条件和搜索结果
                                mLastSearchList = resultList
                                hideSearching() //隐藏loading
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
     * 取消搜索栏的订阅
     */
    private fun destroySearchObservable() {
        ALog.i(TAG, "destroySearchObservable")
        if (mTextWatcher != null) {
            common_search_text?.removeTextChangedListener(mTextWatcher)
            mTextWatcher = null
        }
    }

    /**
     * 搜索栏输入触发回调类
     */
    abstract class OnSearchActionListener<in T> {
        abstract fun onSearchResult(filter: String, results: List<T>)    //返回结果列表
        abstract fun onSearchNull(results: List<T>)   //标示搜索栏为空的情况
        abstract fun onMatch(data: T, compare: String): Boolean  //判断指定数据与输入字符串是否匹配
        open fun onSearchClick(searchText: String) {//点击软键盘的搜索触发的

        }
    }

}