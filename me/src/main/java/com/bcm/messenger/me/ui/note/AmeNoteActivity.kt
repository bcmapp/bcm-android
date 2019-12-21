package com.bcm.messenger.me.ui.note

import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.adapter.ListDataSource
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.me.R
import com.bcm.messenger.me.bean.BcmNote
import com.bcm.messenger.me.logic.AmeNoteLogic
import com.bcm.messenger.utility.*
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.me_note_activity.*
import kotlinx.android.synthetic.main.me_note_guild_view_layout.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by bcm.social.01 on 2017/7/4.
 */
@Route(routePath = ARouterConstants.Activity.NOTE)
class AmeNoteActivity : SwipeBaseActivity(), AmeRecycleViewAdapter.IViewHolderDelegate<BcmNote> {
    private val noteLogic = AmeNoteLogic.getInstance()
    private val dataSource = object :ListDataSource<BcmNote>() {
        override fun getItemId(position: Int): Long {
            return EncryptUtils.byteArrayToLong(getData(position).topicId.toByteArray())
        }
    }

    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        this.setContentView(R.layout.me_note_activity)



        EventBus.getDefault().register(this)

        note_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                toEdit("")
            }

        })

        me_note_empty_create.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            toEdit("")
        }

        me_note_list.layoutManager = LinearLayoutManager(this)
        //me_note_list.itemAnimator = DefaultItemAnimator()
        val adapter = AmeRecycleViewAdapter(this, dataSource)
        adapter.setViewHolderDelegate(this)
        adapter.setHasStableIds(true)
        me_note_list.adapter = adapter

        updateNoteList()

        val d = getDrawable(R.drawable.common_content_warning_icon)
        d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
        me_note_empty_note_tip.text = StringAppearanceUtil.addImage("  " + getString(R.string.me_note_if_you_lost_your_password_or_delete_the_app_you_will_lose_all_your_data_in_the_vault),
                d, 0)
    }

    private fun toEdit(topicId: String) {
        val intent = Intent(this, AmeNoteEditorActivity::class.java)
        intent.putExtra(AmeNoteEditorActivity.TOPIC_ID, topicId)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        noteLogic.lock()
    }

    override fun onResume() {
        super.onResume()
        if (noteLogic.isLocked()) {
            finish()
        }
    }

    @Subscribe
    fun onEvent(event:AmeNoteLogic.NoteListChangedEvent) {
        updateNoteList()
    }

    private fun updateNoteList() {
        val list = noteLogic.getNoteList()
        if (list.isNotEmpty()) {
            note_title_bar.setCenterText(getString(R.string.me_note_list_title))
            me_note_guild_view.visibility = View.GONE
            me_note_list.visibility = View.VISIBLE
        } else {
            note_title_bar.setCenterText("")
            me_note_guild_view.visibility = View.VISIBLE
            me_note_list.visibility = View.GONE
        }

        dataSource.updateDataSource(list)
    }

    private fun switchNotePin(topicId: String, pin:Boolean) {
        noteLogic.pinNote(topicId, pin)
    }

    private fun deleteNote(topicId: String) {
        val wself = WeakReference(this)
        AmePopup.bottom.newBuilder()
                .withTitle(getString(R.string.me_note_delete_note_title))
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_note_delete), AmeBottomPopup.PopupItem.CLR_RED) {
                    wself.get()?.noteLogic?.deleteNote(topicId) {
                        succeed, error ->
                        ALog.i("AmeNoteActivity", "delete succeed:$succeed, $error")
                    }
                })
                .withDoneTitle(getString(R.string.common_cancel))
                .show(this)
    }

    override fun getViewHolderType(adapter: AmeRecycleViewAdapter<BcmNote>, position: Int, data: BcmNote): Int {
        return R.layout.me_note_cell_layout
    }

    override fun bindViewHolder(adapter: AmeRecycleViewAdapter<BcmNote>, viewHolder: AmeRecycleViewAdapter.ViewHolder<BcmNote>) {
        (viewHolder as NoteHolder).update()
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<BcmNote>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<BcmNote> {
        return NoteHolder(LayoutInflater.from(this).inflate(viewType, parent, false))
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<BcmNote>, viewHolder: AmeRecycleViewAdapter.ViewHolder<BcmNote>) {
        if (QuickOpCheck.getDefault().isQuick) {
            return
        }
        toEdit(viewHolder.getData()?.topicId?:"")
    }

    override fun onViewLongClicked(adapter: AmeRecycleViewAdapter<BcmNote>, viewHolder: AmeRecycleViewAdapter.ViewHolder<BcmNote>): Boolean {
        val data = (viewHolder as NoteHolder).getData()?:return false

        val menuItems = listOf(
                BcmPopupMenu.MenuItem(if (data.pin) getString(R.string.me_note_item_cancel_pin) else getString(R.string.me_note_item_pin)),
                BcmPopupMenu.MenuItem(getString(R.string.me_note_item_delete))
        )

        BcmPopupMenu.Builder(this)
                .setMenuItem(menuItems)
                .setAnchorView(viewHolder.itemView)
                .setSelectedCallback { index ->
                    val topicId = viewHolder.getData()?.topicId?:return@setSelectedCallback
                    when (index) {
                        0 -> switchNotePin(topicId, !data.pin)
                        1 -> deleteNote(topicId)
                    }
                }
                .show(viewHolder.lastTouchPoint.x, viewHolder.lastTouchPoint.y)

        return true
    }



    data class NoteHolder(private val view:View):AmeRecycleViewAdapter.ViewHolder<BcmNote>(view) {
        private val topic:TextView = view.findViewById(R.id.note_cell_topic)
        private val timestamp:TextView = view.findViewById(R.id.note_cell_timestamp)
        val lastTouchPoint = Point()

        init {
            view.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    lastTouchPoint.set(event.rawX.toInt(), event.y.toInt() - view.height)
                }
                return@setOnTouchListener false
            }
        }

        fun update() {
            val data = this.getData()
            if (null != data) {
                topic.text = data.topic
                timestamp.text = formatTime()

                if (data.pin) {
                    timestamp.setDrawableRight(R.drawable.me_note_item_16_pin_icon, 16.dp2Px())
                } else {
                    timestamp.setDrawableRight(0)
                }
            }
        }

        private fun formatTime(): String {
            val timestamp = getData()?.timestamp ?: return ""
            val selectedLocale = getSelectedLocale(AppContextHolder.APP_CONTEXT)
            return DateUtils.getNoteTimSpan(AppContextHolder.APP_CONTEXT, timestamp, if (selectedLocale.language == Locale.CHINESE.language) selectedLocale else Locale.US)

        }
    }
}