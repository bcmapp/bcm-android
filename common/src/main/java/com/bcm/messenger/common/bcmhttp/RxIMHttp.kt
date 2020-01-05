package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.utility.bcmhttp.facade.RxHttpWrapper

object RxIMHttp:AccountContextMap<RxHttpWrapper>({
    RxHttpWrapper(IMHttp.get(it))
})