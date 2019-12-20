package com.bcm.messenger.common.grouprepository.events

class GroupShareSettingRefreshEvent(val gid:Long, val shareCode:String, val shareSetting:String, val shareSettingSign:String, val shareConfirmSign:String, val needConfirm:Int) {
}