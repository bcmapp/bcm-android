package com.bcm.messenger.common.ui.popup

import com.bcm.messenger.common.ui.popup.centerpopup.*
import com.bcm.messenger.common.ui.popup.bottompopup.*

/**
 * Created by bcm.social.01 on 2018/5/31.
 */
object AmePopup {
    //中间弹窗
    val center: AmeCenterPopup = AmeCenterPopup.instance()
    //底部弹窗
    val bottom: AmeBottomPopup = AmeBottomPopup.instance()
    //加载中动画弹窗
    val loading: AmeLoadingPopup = AmeLoadingPopup()
    //带提示的loading窗
    val tipLoading = AmeTipLoadingPopup()
    //处理结果弹窗
    val result: AmeResultPopup = AmeResultPopup()
    //下载中提示弹窗
    val download: AmeDownloadPopup = AmeDownloadPopup()
    //进度弹窗
    val progress: AmeProgressPopup = AmeProgressPopup()
    //动态弹窗
    val anim: AmeAnimPopup = AmeAnimPopup.instance()
}