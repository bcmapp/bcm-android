package com.bcm.messenger.common.ui.popup

import com.bcm.messenger.common.ui.popup.centerpopup.*
import com.bcm.messenger.common.ui.popup.bottompopup.*

/**
 * Created by bcm.social.01 on 2018/5/31.
 */
object AmePopup {
    //
    val center: AmeCenterPopup = AmeCenterPopup.instance()
    //
    val bottom: AmeBottomPopup = AmeBottomPopup.instance()
    //
    val loading: AmeLoadingPopup = AmeLoadingPopup()
    //loading
    val tipLoading = AmeTipLoadingPopup()
    //
    val result: AmeResultPopup = AmeResultPopup()
    //
    val download: AmeDownloadPopup = AmeDownloadPopup()
    //
    val progress: AmeProgressPopup = AmeProgressPopup()
    //
    val anim: AmeAnimPopup = AmeAnimPopup.instance()
}