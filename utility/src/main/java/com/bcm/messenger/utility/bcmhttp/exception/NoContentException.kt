package com.bcm.messenger.utility.bcmhttp.exception

import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp

/**
 * Created by bcm.social.01 on 2019/3/13.
 */
class NoContentException : BaseHttp.HttpErrorException(204,"") {
}