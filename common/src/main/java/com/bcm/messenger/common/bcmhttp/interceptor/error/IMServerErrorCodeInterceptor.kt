package com.bcm.messenger.common.bcmhttp.interceptor.error

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.R
import com.bcm.messenger.common.bcmhttp.exception.VersionTooLowException
import com.bcm.messenger.common.event.ClientAccountDisabledEvent
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import com.bcm.messenger.utility.logger.ALog
import com.google.gson.JsonParseException
import okhttp3.Response
import org.greenrobot.eventbus.EventBus
import org.whispersystems.signalservice.api.push.exceptions.*
import org.whispersystems.signalservice.internal.push.DeviceLimit
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException
import org.whispersystems.signalservice.internal.push.MismatchedDevices
import org.whispersystems.signalservice.internal.push.StaleDevices
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException
import java.io.IOException

class IMServerErrorCodeInterceptor(private val accountContext: AccountContext) : BcmErrorInterceptor() {
    private val TAG = "IMServerError"

    override fun onError(response: Response) {
        when (val code = response.code()) {
            413 -> throw RateLimitException("Rate limit exceeded: $code")
            401, 403 -> throw AuthorizationFailedException("Authorization failed!")
            404 -> throw NotFoundException("Not found")
            409 -> {
                var mismatchedDevices: MismatchedDevices? = null
                try {
                    val responseBody = response.body()?.string()?:""
                    mismatchedDevices = if (responseBody.isNotEmpty()) {
                        GsonUtils.fromJson(responseBody, MismatchedDevices::class.java)
                    } else {
                        null
                    }
                } catch (e: JsonParseException) {
                    ALog.e(TAG, "error:$code", e)
                    throw NonSuccessfulResponseCodeException("Bad response: $code")
                } catch (e: IOException) {
                    throw PushNetworkException(e)
                }

                if (mismatchedDevices?.missingDevices?.isNotEmpty() == true || mismatchedDevices?.extraDevices?.isNotEmpty() == true) {
                    throw MismatchedDevicesException(mismatchedDevices)
                }
            }
            410 -> {
                val staleDevices: StaleDevices

                try {
                    val responseBody = response.body()?.string()?:""
                    staleDevices = GsonUtils.fromJson(responseBody, StaleDevices::class.java)
                } catch (e: JsonParseException) {
                    throw NonSuccessfulResponseCodeException("Bad response: $code")
                } catch (e: IOException) {
                    throw PushNetworkException(e)
                }

                throw StaleDevicesException(staleDevices)
            }
            411 -> {
                val deviceLimit: DeviceLimit

                try {
                    val responseBody = response.body()?.string()?:""
                    deviceLimit = GsonUtils.fromJson(responseBody, DeviceLimit::class.java)
                } catch (e: JsonParseException) {
                    throw NonSuccessfulResponseCodeException("Bad response: $code")
                } catch (e: IOException) {
                    throw PushNetworkException(e)
                }

                throw DeviceLimitExceededException(deviceLimit)
            }
            417 -> throw ExpectationFailedException()
            ServerCodeUtil.CODE_LOW_VERSION -> throw VersionTooLowException(code, AppUtil.getString(R.string.common_too_low_version_notice))
            ServerCodeUtil.CODE_TOKEN_EXPIRE -> {
                EventBus.getDefault().post(ClientAccountDisabledEvent(accountContext, ClientAccountDisabledEvent.TYPE_EXPIRE))
            }
            else -> {

            }
        }

        //，，，mainThread，
        ServerCodeUtil.storeErrorCode(response.code())
    }
}