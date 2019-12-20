package com.bcm.messenger.adhoc.sdk

import com.bcm.imcore.IAdHocBinder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.ble.BleUtil
import com.bcm.messenger.utility.wifi.WiFiUtil

class AdHocSdkHelper(sdkApi:IAdHocBinder, stateChanged:()->Unit) {
    private val TAG = "AdHocSdkHelper"
    val proxy = ServiceProxy(sdkApi, stateChanged)
    val broadcaster = Broadcaster(sdkApi, stateChanged)
    val scanner = Scanner(sdkApi, stateChanged)

    private enum class STATE {
        DOING,
        DONE,
        INIT;
    }

    inner class ServiceProxy(private val sdkApi:IAdHocBinder, private val stateChanged:()->Unit) {
        private var state:STATE = STATE.INIT

        fun start(finish:()->Unit) {
            if (state != STATE.INIT) {
                finish()
                return
            }
            state = STATE.DOING

            sdkApi.startVpn(AdHocResult {succeed ->
                if (succeed && WiFiUtil.isEnable()) {
                    ALog.i(TAG, "hotspot createAdHocService")
                    sdkApi.createAdHocService(AdHocResult{
                        state = if (it) {
                            STATE.DONE
                        } else {
                            STATE.INIT
                        }
                        stateChanged()
                        finish()
                    })
                } else {
                    STATE.INIT
                    stateChanged()
                    finish()
                }
            })

            stateChanged()
        }

        fun stop(finish:()->Unit) {
            if (state == STATE.INIT) {
                finish()
                return
            }

            sdkApi.stopVpn(AdHocResult {

            })

            ALog.i(TAG, "hotspot destroyAdHocService")
            sdkApi.destroyAdHocService(AdHocResult {
                if (it) {
                    state = STATE.INIT
                    stateChanged()
                }
                finish()
            })
        }

        fun isRunning(): Boolean {
            return state != STATE.INIT
        }
    }

    inner class Broadcaster(private val sdkApi:IAdHocBinder, private val stateChanged:()->Unit)  {
        private var state:STATE = STATE.INIT

        fun start(finish:()->Unit) {
            if (sdkApi.isBroadcastingAdHocService) {
                state = STATE.DONE
                stateChanged()
                finish()
                return
            }

            if (state != STATE.INIT) {
                finish()
                return
            }

            if (!BleUtil.isEnable() || !BleUtil.isSupport()) {
                finish()
                return
            }

            state = STATE.DOING
            ALog.i(TAG, "Broadcaster startBroadcastAdHocService")
            sdkApi.startBroadcastAdHocService(AdHocResult{
                state = if (it) {
                    STATE.DONE
                } else {
                    STATE.INIT
                }

                stateChanged()
                finish()
            })

            stateChanged()
        }

        fun stop(finish:()->Unit) {
            if (state == STATE.INIT) {
                finish()
                return
            }

            ALog.i(TAG, "Broadcaster stopBroadcastAdHocService")
            sdkApi.stopBroadcastAdHocService(AdHocResult {
                if (it) {
                    state = STATE.INIT
                    stateChanged()
                }
                finish()
            })
        }

        fun isRunning(): Boolean {
            return state != STATE.INIT && sdkApi.isBroadcastingAdHocService
        }
    }

    inner class Scanner(private val sdkApi:IAdHocBinder, private val stateChanged:()->Unit)  {
        private var state:STATE = STATE.INIT

        fun start(finish:()->Unit) {
            if (state != STATE.INIT) {
                finish()
                return
            }

            if (!BleUtil.isEnable() || !BleUtil.isSupport()) {
                finish()
                return
            }
            state = STATE.DOING

            ALog.i(TAG, "Scanner startSearchAdHocService")
            sdkApi.startSearchAdHocService(AdHocResult{
                state = if (it) {
                    STATE.DONE
                } else {
                    STATE.INIT
                }

                stateChanged()
                finish()
            })

            stateChanged()
        }

        fun stop(finish:()->Unit) {
            if (state == STATE.INIT) {
                finish()
                return
            }

            ALog.i(TAG, "Scanner stopSearchAdHocService")
            sdkApi.stopSearchAdHocService(AdHocResult {
                if (it) {
                    state = STATE.INIT
                    stateChanged()
                }
                finish()
            })
        }

        fun isRunning(): Boolean {
            return state != STATE.INIT
        }
    }
}