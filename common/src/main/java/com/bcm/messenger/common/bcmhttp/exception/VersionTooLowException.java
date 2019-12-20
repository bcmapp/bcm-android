package com.bcm.messenger.common.bcmhttp.exception;

import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp;

import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.IOException;

/**
 * 版本太低的异常
 * Created by wjh on 2019/7/11
 */
public class VersionTooLowException extends BaseHttp.HttpErrorException {

    public VersionTooLowException(int code, String message) {
        super(code, message);
    }
}
