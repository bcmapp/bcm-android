package com.bcm.netswitchy;

import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;

/**
 * 用于ssr hook实现方式的系统工具类
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

    /**
     * 开启本地拦截服务
     *
     * @param tcpPort
     */
    public native void startHook(String host, int tcpPort, String authName, String password);

    /**
     * 关闭本地拦服务
     */
    public native void stopHook();

    /**
     * 添加指定代理黑名单，防止死循环
     *
     * @param orderId
     * @param domain
     */
    public native void addProxyBlacklist(String orderId, String domain);

    /**
     * 添加网关黑名单，防止死循环
     *
     * @param blacklists
     */
    public native void addBlacklist(String... blacklists);

    /**
     * 删除网关黑名单
     *
     * @param blacklists
     */
    public native void removeBlacklist(String... blacklists);

    public native String lookupIpAddress(String host);

    /**
     * 开启hook服务
     *
     * @param listenAddress
     * @param listenPort
     */
    public void proxyHook(String listenAddress, int listenPort) {
        ALog.i(TAG, "proxyHook");
        proxyHook(listenAddress, listenPort, "", "");
    }

    public void proxyHook(String listenAddress, int listenPort, String authName, String authPassword) {
        ALog.i(TAG, "proxyHook 1");
        mListenAddress = listenAddress;
        if (listenAddress != null) {
            addBlacklist(listenAddress);
        }
        startHook(listenAddress, listenPort, authName, authPassword);
    }

    /**
     * 关闭hook服务
     */
    public void proxyUnhook() {
        ALog.i(TAG, "proxyUnhook");
        if (mListenAddress != null) {
            removeBlacklist(mListenAddress);
        }
        stopHook();
    }
}
