package com.bcm.messenger.chats.clean

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.provider.bean.ConversationStorage
import com.bcm.messenger.common.ui.CommonSettingItem
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StringAppearanceUtil

/**
 * Created by bcm.social.01 on 2018/11/22.
 */
class CleanAllConversationStorageSelectionViewCreater(var storage: ConversationStorage, val selected:(type:Int)->Unit): AmeBottomPopup.CustomViewCreator {
    private var itemFiles: CommonSettingItem? = null
    private var itemVideos: CommonSettingItem? = null
    private var itemImages: CommonSettingItem? = null
    private var clearSizeView:TextView? = null

    private var videoSelected = true
    private var imageSelected = true
    private var fileSelected = true

    override fun onCreateView(parent: ViewGroup): View? {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chats_clean_storage_pop_view, parent, true)
        itemImages = view.findViewById(R.id.clean_all_photos)
        itemVideos = view.findViewById(R.id.clean_all_videos)
        itemFiles = view.findViewById(R.id.clean_all_files)
        clearSizeView = view.findViewById(R.id.clean_clear_view)

        itemFiles?.setOnClickListener{
            fileSelected = !fileSelected
            itemFiles?.setLogo(stateIcon(fileSelected))
            updateClearSize()
        }

        itemVideos?.setOnClickListener{
            videoSelected = !videoSelected
            itemVideos?.setLogo(stateIcon(videoSelected))
            updateClearSize()
        }

        itemImages?.setOnClickListener{
            imageSelected = !imageSelected
            itemImages?.setLogo(stateIcon(imageSelected))
            updateClearSize()
        }


        clearSizeView?.setOnClickListener{
            selected(state2Type())
        }

        updateView()
        return view
    }

    override fun onDetachView() {

    }

    private fun stateIcon(selected:Boolean): Int {
        return if (selected){
            R.drawable.common_tick_green_icon
        } else {
            R.drawable.common_tick_empty_icon
        }
    }

    private fun state2Type(): Int{
        var type = 0;
        if (videoSelected){
            type = type.or(ConversationStorage.TYPE_VIDEO)
        }

        if (imageSelected){
           type = type.or(ConversationStorage.TYPE_IMAGE)
        }

        if (fileSelected){
            type = type.or(ConversationStorage.TYPE_FILE)
        }

        return type
    }

    private fun selectedSize(): Long {
        var size = 0L;
        if (videoSelected){
           size += storage.videoSize
        }

        if (imageSelected){
            size += storage.imageSize
        }

        if (fileSelected){
            size += storage.fileSize
        }

        return size
    }

    private fun updateClearSize(){
        clearSizeView?.text = AppContextHolder.APP_CONTEXT.getString(R.string.chats_clear_size_format,
                StringAppearanceUtil.formatByteSizeString(selectedSize()))
    }

    fun updateStorage(storage: ConversationStorage){
        this.storage = storage
        updateView()
    }

    private fun updateView(){
        itemFiles?.setTip(content = StringAppearanceUtil.formatByteSizeString(storage.fileSize))
        itemVideos?.setTip(content = StringAppearanceUtil.formatByteSizeString(storage.videoSize))
        itemImages?.setTip(content = StringAppearanceUtil.formatByteSizeString(storage.imageSize))
        updateClearSize()
    }
}