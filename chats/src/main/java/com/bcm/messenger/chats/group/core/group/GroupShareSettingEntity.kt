package com.bcm.messenger.chats.group.core.group

import com.bcm.messenger.utility.proguard.NotGuard

class GroupShareSettingEntity(var share_enabled: Int, //scan to join group, 0:not support，1:support
                              var share_code: String, //share code
                              var share_epoch: Int   // modified by configuration each time
 ): NotGuard