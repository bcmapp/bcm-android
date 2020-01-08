package com.bcm.messenger.me.ui.note

import android.graphics.PointF
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmeNoteLogic
import com.bcm.messenger.me.provider.UserModuleImp
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.me_note_editor_activity.*
import java.lang.ref.WeakReference

class AmeNoteEditorActivity : SwipeBaseActivity() {
    companion object {
        const val TOPIC_ID = "note_topic_id"
    }

    private var topicId: String = ""
    private var initContentHash = ""
    private lateinit var noteLogic:AmeNoteLogic
    private var saveEvent: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_note_editor_activity)
        topicId = intent.getStringExtra(TOPIC_ID) ?: ""

        if (!accountContext.isLogin) {
            finish()
            return
        }
        noteLogic = (AmeModuleCenter.user(accountContext) as UserModuleImp).getNote()

        note_editor_title_bar.setCenterText(getString(R.string.me_note_new_note_title))

        note_editor_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                showPopMenu()
            }
        })

        updateTopic(topicId)

        if (topicId.isNotBlank()) {
            val wself = WeakReference(this)
            noteLogic.loadNoteContent(topicId) {
                wself.get()?.initEditText(it)
            }
        } else {
            note_edit.post {
                note_edit.requestFocus()
            }
            note_editor_title_bar.disableRight()
        }


        note_edit.setOnTouchListener(object : View.OnTouchListener {
            private val movePoint = PointF(-1f, -1f)
            private var hideKeyboardOnUp = false
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        movePoint.set(event.x, event.y)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (hideKeyboardOnUp) {
                            hideKeyboardOnUp = false
                            movePoint.set(-1f, -1f)
                            hideKeyboard()
                            note_edit.clearFocus()
                            return true
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        ALog.i("AmeNoteEditorActivity", "dis:${event.y - movePoint.y}")

                        if (movePoint.y >= 0 && event.y - movePoint.y > 300) {
                            hideKeyboardOnUp = true
                        } else if (hideKeyboardOnUp) {
                            hideKeyboardOnUp = false
                            movePoint.set(-1f, -1f)
                        }
                    }
                }
                return false
            }
        })

        note_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.isNotBlank() == true) {
                    if (!note_editor_title_bar.isRightEnable()) {
                        note_editor_title_bar.enableRight()
                    }

                    val wself = WeakReference(this@AmeNoteEditorActivity)
                    saveEvent?.dispose()
                    saveEvent = AmeDispatcher.io.dispatch({
                        wself.get()?.delaySave()
                    }, 1200)

                } else {
                    note_editor_title_bar.disableRight()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun delaySave() {
        if (isFinishing) {
            return
        }

        saveEvent = null
        if (note_edit.text.isNotEmpty()) {
            saveNote()
        }
    }

    private fun initEditText(topicContent: String) {
        if (isFinishing) {
            return
        }

        note_edit.setText(topicContent)
        if (topicContent.isNotBlank()) {
            initContentHash = EncryptUtils.encryptSHA1ToString(topicContent)
        }


        //val note = noteLogic.getNote(topicId)
        //note_edit.setSelection(Math.min(topicContent.length, note?.lastEditPosition?:topicContent.length))
    }

    override fun onDestroy() {
        super.onDestroy()
        saveEvent?.dispose()
        saveEvent = null
        saveNote()
    }

    override fun onResume() {
        super.onResume()

        if (noteLogic.isLocked()) {
            finish()
        }
    }

    private fun saveNote() {
        val newNote = note_edit.text.toString()
        if (newNote.isNotBlank() && initContentHash != EncryptUtils.encryptSHA1ToString(newNote)) {
            val weakThis = WeakReference(this)
            if (topicId.isBlank()) {
                noteLogic.addNote("", newNote, note_edit.selectionStart) { succeed, topicId, error ->
                    weakThis.get()?.updateTopic(topicId)
                }
            } else {
                noteLogic.updateNote(topicId, newNote, note_edit.selectionStart) { succeed, error ->
                    weakThis.get()?.updateTopic(weakThis.get()?.topicId ?: "")
                }
            }
        } else if (newNote.isBlank() && topicId.isNotEmpty()) {
            noteLogic.deleteNote(topicId) { _, _ ->
            }
        }
    }

    private fun updateTopic(topicId: String) {
        if (isFinishing) {
            return
        }
        this.topicId = topicId

        val note = noteLogic.getNote(topicId)
        if (null != note) {
            note_editor_title_bar.setCenterText(note.topic)
        }

        val topicContent = note_edit.text.toString()
        if (topicContent.isNotBlank()) {
            initContentHash = EncryptUtils.encryptSHA1ToString(topicContent)
        }
    }

    private fun showPopMenu() {
        hideKeyboard()
        AmePopup.bottom.newBuilder()
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_note_forward)) {
                    forward()
                })
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_note_delete)) {
                    showDeleteMenu()
                })
                .withDoneTitle(getString(R.string.common_cancel))
                .show(this)
    }

    private fun showDeleteMenu() {
        val wself = WeakReference(this)
        AmePopup.bottom.newBuilder()
                .withTitle(getString(R.string.me_note_delete_note_title))
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_note_delete)) {
                    noteLogic.deleteNote(topicId) { succeed, error ->
                        if (succeed) {
                            wself.get()?.deleteNote()
                        }
                    }
                })
                .withDoneTitle(getString(R.string.common_cancel))
                .show(this)
    }

    private fun deleteNote() {
        if (isFinishing) {
            return
        }
        note_edit.setText("")
        finish()
    }

    private fun forward() {
        val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
        if (null != provider) {
            val newNote = note_edit.text.toString()
            provider.systemForward(this, newNote)
        }

    }
}