package com.bcm.messenger.common.core;

import com.bcm.messenger.utility.proguard.NotGuard;
import com.google.gson.annotations.SerializedName;


/**
 * common server data
 *
 * @author ling in 2018/5/3
 **/
public class ServerResult<T> implements NotGuard {
    public static final int RESULT_SUCCESS = 0;

    /**
     * code
     */
    @SerializedName("error_code")
    public int code;

    /**
     * message
     */
    @SerializedName("error_msg")
    public String msg;

    /**
     * data
     */
    @SerializedName("result")
    public T data;

    /**
     * 
     * true:0；false：else
     *
     * @return 
     */
    public boolean isSuccess() {
        return code == RESULT_SUCCESS;
    }

    public boolean isNotSuccess(){
        return !isSuccess();
    }
}
