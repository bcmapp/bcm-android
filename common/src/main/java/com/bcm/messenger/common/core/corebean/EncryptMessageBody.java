package com.bcm.messenger.common.core.corebean;

import com.bcm.messenger.utility.proguard.NotGuard;

public class EncryptMessageBody implements NotGuard {

    /**
     * plainText : aaa
     * sign : aaa_digest
     */

    private String plainText;
    private String sign;

    public String getPlainText() {
        return plainText;
    }

    public void setPlainText(String plainText) {
        this.plainText = plainText;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }
}
