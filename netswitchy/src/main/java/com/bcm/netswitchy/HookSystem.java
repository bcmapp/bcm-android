package com.bcm.netswitchy;

import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;

/**
 * Created by wjh on 2019/3/4
 */
public class HookSystem implements NotGuard {

    private static final String TAG = "HookSystem";

    static {
        try {
            System.loadLibrary("bcm_hook");
        } catch (Throwable ex) {
            ALog.e(TAG, "loadLibrary error", ex);
        }
    }

    private String mListenAddress;
    public native void startHook(int tcpPort, int udpPort);
    public native void stopHook();
    public native void addProxyBlacklist(String orderId, String domain);
    public native void addBlacklist(String... blacklists);
    public native void removeBlacklist(String... blacklists);

    public void proxyHook(String listenAddress, int listenPort) {
        ALog.i(TAG, "proxyHook");
        mListenAddress = listenAddress;
        if (listenAddress != null) {
            addBlacklist(listenAddress);
        }
        startHook(listenPort, 0);
    }

    public void proxyUnhook() {
        ALog.i(TAG, "proxyUnhook");
        if (mListenAddress != null) {
            removeBlacklist(mListenAddress);
        }
        stopHook();
    }
}
