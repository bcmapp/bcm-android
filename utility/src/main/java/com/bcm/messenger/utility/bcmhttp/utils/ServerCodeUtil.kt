package com.bcm.messenger.utility.bcmhttp.utils
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.orhanobut.logger.Logger

/**
 * Created by wjh on 2019/7/2
 */
object ServerCodeUtil {

    const val CODE_LOW_VERSION = 461
    const val CODE_SERVICE_500 = 500
    const val CODE_SERVICE_460 = 460
    const val CODE_TOKEN_EXPIRE = 401
    const val CODE_SERVICE_404 = 404
    const val CODE_PARSE_ERROR = 9999
    const val CODE_CONN_ERROR = 9998

    private var mWebSocketCode: Int = 0
        @Synchronized set
        @Synchronized get

    private var mServerErrorCode: Int = 0
        @Synchronized set
        @Synchronized get

    fun storeErrorCode(error: Int) {
        Logger.i("storeErrorCode: $error")
        mServerErrorCode = error
    }

    fun pullLastErrorCode(): Int {
        Logger.i("pullLastErrorCode: $mServerErrorCode")
        val last = mServerErrorCode
        mServerErrorCode = 0
        return last
    }

    fun storeWebSocketError(error: Int) {
        Logger.i("storeWebSocketError: $error")
        mWebSocketCode = error
    }

    fun pullWebSocketError(): Int {
        Logger.i("pullWebSocketError: $mWebSocketCode")
        val last = mWebSocketCode
        mWebSocketCode = 0
        return last
    }

    fun getNetStatusCode(error: Throwable): Int {
        return if (error is BaseHttp.HttpErrorException) {
            error.code
        }else {
            0
        }
    }
}