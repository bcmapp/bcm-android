package com.bcm.messenger.common.bcmhttp.configure.sslfactory

import com.bcm.messenger.common.R
import com.bcm.messenger.utility.AppContextHolder
import org.whispersystems.signalservice.api.push.TrustStore
import java.io.InputStream

class IMServerKeyStore : TrustStore {
    override fun getKeyStoreInputStream(): InputStream {
        return AppContextHolder.APP_CONTEXT.resources.openRawResource(R.raw.bcm_cert)
    }

    override fun getKeyStorePassword(): String {
        return ""
    }

}