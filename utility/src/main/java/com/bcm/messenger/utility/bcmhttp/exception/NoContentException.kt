package com.bcm.messenger.utility.bcmhttp.exception

import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp

class NoContentException : BaseHttp.HttpErrorException(204,"") {
}