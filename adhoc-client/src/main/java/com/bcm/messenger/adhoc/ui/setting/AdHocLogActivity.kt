package com.bcm.messenger.adhoc.ui.setting

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.sdk.AdHocSDK
import com.bcm.messenger.adhoc.sdk.LogData
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.utils.dp2Px
import com.orhanobut.logger.Logger
import kotlinx.android.synthetic.main.adhoc_channel_log_activity.*
import com.bcm.messenger.common.SwipeBaseActivity
import java.text.SimpleDateFormat

class AdHocLogActivity: SwipeBaseActivity(), AmeRecycleViewAdapter.IViewHolderDelegate<LogData> {
    private val dataSource = AdHocSDK.getLogSource()
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS")
    private var canAutoScroll = true

    companion object {
        fun router(context: Context) {
            val intent = Intent(context, AdHocLogActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.adhoc_channel_log_activity)

        adhoc_log_toolbar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                super.onClickLeft()
                finish()
            }
        })

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        layoutManager.reverseLayout = true
        adhoc_log_list.layoutManager = layoutManager
        val adapter = AmeRecycleViewAdapter(this,dataSource)
        adapter.setViewHolderDelegate(this)
        adhoc_log_list.adapter = adapter

        adhoc_log_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!canAutoScroll) {
                            val lastVisibleItem = (recyclerView.layoutManager
                                    as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                            setCanAutoScroll(lastVisibleItem <= 2)
                        }
                    }
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        setCanAutoScroll(false)
                    }
                }
            }
        })

        adhoc_log_list.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val lastVisibleItem = (adhoc_log_list.layoutManager
                        as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                setCanAutoScroll(lastVisibleItem <= 2)

            }
            return@setOnTouchListener false
        }

        dataSource.unLock()
        adhoc_log_list.scrollToPosition(0)

        dataSource.setDataChangedNotify {
            adhoc_log_list.adapter?.notifyDataSetChanged()
            if (canAutoScroll) {
                adhoc_log_list.scrollToPosition(0)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dataSource.setDataChangedNotify {  }
    }

    private fun setCanAutoScroll(canAuto:Boolean) {
        this.canAutoScroll = canAuto
        if (canAuto) {
            if(dataSource.unLock()) {
                adhoc_log_list.scrollToPosition(0)
            }
        } else {
            dataSource.lock()
        }
    }


    override fun createViewHolder(adapter: AmeRecycleViewAdapter<LogData>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<LogData> {
        val textView = TextView(this)
        textView.setPadding(2.dp2Px(), 2.dp2Px(), 2.dp2Px(), 2.dp2Px())
        return AmeRecycleViewAdapter.ViewHolder(textView)
    }

    override fun bindViewHolder(adapter: AmeRecycleViewAdapter<LogData>, viewHolder: AmeRecycleViewAdapter.ViewHolder<LogData>) {
        super.bindViewHolder(adapter, viewHolder)
        val it = viewHolder.getData() as LogData
        if (it.time == 0L) {
            ALog.i("AdHocLogActivity", "default data")
            return
        }

        val textView = viewHolder.itemView as TextView

        val timeText = "${sdf.format(it.time)}: "
        val ss = SpannableString("$timeText${it.message}")
        ss.setSpan(StyleSpan(Typeface.BOLD), 0, timeText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(ForegroundColorSpan(
                ContextCompat.getColor(this, when(it.logLevel) {
                    Logger.VERBOSE -> R.color.adhoc_verbose
                    Logger.DEBUG -> R.color.adhoc_debug
                    Logger.INFO -> R.color.adhoc_info
                    Logger.WARN -> R.color.adhoc_warn
                    Logger.ERROR -> R.color.adhoc_error
                    else -> R.color.adhoc_verbose
                })), timeText.length, timeText.length + it.message.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = ss
    }
}