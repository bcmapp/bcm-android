package com.bcm.messenger.common.crypto

import com.bcm.messenger.common.utils.AccountContextMap

object AccountMasterSecret:AccountContextMap<MasterSecret>({
    MasterSecretUtil.getMasterSecret(it, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
})