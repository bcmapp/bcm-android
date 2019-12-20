package com.bcm.messenger.login.bean

import com.bcm.messenger.utility.proguard.NotGuard

/**
 * Created by zjl on 2018/9/3.
 */
data class ChallengeResult(val difficulty: Int, val nonce: Long) : NotGuard