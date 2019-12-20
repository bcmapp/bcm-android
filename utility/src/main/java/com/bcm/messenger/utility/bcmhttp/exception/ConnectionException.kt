package com.bcm.messenger.utility.bcmhttp.exception

import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import java.lang.Exception

class ConnectionException(e:Exception):BaseHttp.HttpErrorException(ServerCodeUtil.CODE_CONN_ERROR, "", e) {
}