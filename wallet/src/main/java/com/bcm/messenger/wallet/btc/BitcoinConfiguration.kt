package com.bcm.messenger.wallet.btc

import android.content.SharedPreferences
import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.wallet.btc.jsonrpc.TcpEndpoint
import com.bcm.messenger.wallet.btc.net.HttpsEndpoint
import com.bcm.messenger.wallet.btc.net.ServerEndpoints
import com.bcm.messenger.wallet.btc.net.TorHttpsEndpoint
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.utils.BCMWalletManager
import okhttp3.Request

// A set of classes for parsing nodes.json file

// MyceliumNodesResponse is intended for parsing nodes.json file
class MyceliumNodesResponse(@SerializedName("BTC-testnet") val btcTestnet: BTCNetResponse,
                            @SerializedName("BTC-mainnet") val btcMainnet: BTCNetResponse) : NotGuard

// BTCNetResponse is intended for parsing nodes.json file
class BTCNetResponse(@SerializedName("WAPI") val normal: NormalResponse, @SerializedName("electrumx") val electrumx: ElectrumXResponse) : NotGuard

// ElectrumXResponse is intended for parsing nodes.json file
class ElectrumXResponse(val primary: Array<ElectrumServerResponse>, val backup: Array<ElectrumServerResponse>) : NotGuard

// ElectrumServerResponse is intended for parsing nodes.json file
class ElectrumServerResponse(@SerializedName("url") val url: String, @SerializedName("cert-sha1") val cert: String) : NotGuard

class NormalResponse(val primary: Array<NormalServerResponse>, val backup: Array<NormalServerResponse>) : NotGuard

class NormalServerResponse(@SerializedName("url") val url: String, @SerializedName("cert-sha1") val cert: String) : NotGuard

class BitcoinConfiguration(private val prefs: SharedPreferences) {

    private var electrumxServersChangedListener: ElectrumxServersChangedListener? = null
    fun setTcpEndpoinsListChangedListener(electrumxServersChangedListener: ElectrumxServersChangedListener?) {
        this.electrumxServersChangedListener = electrumxServersChangedListener
    }

    private var normalServersChangedListener: NormalServersChangedListener? = null
    fun setHttpEndpointsListChangedListener(normalServersChangedListener: NormalServersChangedListener) {
        this.normalServersChangedListener = normalServersChangedListener
    }

    companion object {
        private const val TAG = "BitcoinConfiguration"

        const val PREFS_MYCELIUM_SERVERS = "mycelium_servers"
        const val PREFS_ELECTRUM_SERVERS = "electrum_servers"
        const val PREFS_NORMAL_SERVERS = "api_normal_servers"

        const val TCP_TLS_PREFIX = "tcp-tls://"
        const val AMAZON_S3_STORAGE_ADDRESS = "https://mycelium-wallet.s3.amazonaws.com"

        private val testnetElectrumxEndpoints = arrayOf("tcp-tls://electrumx-b.mycelium.com:4432")

        private val prodnetElectrumxEndpoints = arrayOf("tcp-tls://electrumx.mycelium.com:4431")

        /**
         * Wapi
         */
        private val testnetWapiEndpoints = arrayOf<HttpsEndpoint>(
                HttpsEndpoint("https://mws30.mycelium.com/wapitestnet", "ED:C2:82:16:65:8C:4E:E1:C7:F6:A2:2B:15:EC:30:F9:CD:48:F8:DB"),
                TorHttpsEndpoint("https://ti4v3ipng2pqutby.onion/wapitestnet", "75:3E:8A:87:FA:95:9F:C6:1A:DB:2A:09:43:CE:52:74:27:B1:80:4B")
        )

        /**
         * Wapi
         */
        private val prodnetWapiEndpoints = arrayOf<HttpsEndpoint>(
                // mws 2,6,7,8
                HttpsEndpoint("https://wapi-htz.mycelium.com:4430", "14:83:CB:96:48:E0:7F:96:D0:C3:78:17:98:6F:E3:72:4C:34:E5:07"),
                HttpsEndpoint("https://mws20.mycelium.com/wapi", "65:1B:FF:6B:8C:7F:C8:1C:8E:14:77:1E:74:9C:F7:E5:46:42:BA:E0"),


                // Also try to connect to the nodes via a hardcoded IP, in case the DNS has some problems
                HttpsEndpoint("https://195.201.81.32:4430", "14:83:CB:96:48:E0:7F:96:D0:C3:78:17:98:6F:E3:72:4C:34:E5:07"), // hetzner load balanced
                HttpsEndpoint("https://138.201.206.35/wapi", "B3:42:65:33:40:F5:B9:1B:DA:A2:C8:7A:F5:4C:7C:5D:A9:63:C4:C3"), // mws2

                // tor hidden services
                TorHttpsEndpoint("https://n76y5k3le2zi73bw.onion/wapi", "8D:47:91:A1:EA:9B:CE:E5:A1:9E:38:5B:74:A7:45:0C:88:8F:57:E8"),
                TorHttpsEndpoint("https://vtuao7psnrsot4tb.onion/wapi", "C5:09:C8:37:84:53:65:EE:8E:22:89:32:8F:86:70:49:AD:0A:53:4D"),
                TorHttpsEndpoint("https://rztvro6qgydmujfv.onion/wapi", "A4:09:BC:3A:0E:2D:FE:BF:05:FB:9C:65:DC:82:EA:CF:5D:EE:4D:76"),
                TorHttpsEndpoint("https://slacef5ylu6op7zc.onion/wapi", "EF:62:09:DE:A7:68:15:90:32:93:00:0A:4E:87:05:63:39:B5:87:85")
        )
    }

    @Volatile
    private var myceliumNodesResponse: MyceliumNodesResponse? = null

    //表示上一次请求配置是否失败
    var isLastFetchError = false
        @Synchronized set
        @Synchronized get

    init {
        updateConfig()
    }

    // Makes a request to S3 storage to retrieve nodes.json and parses it to extract electrum servers list
    fun updateConfig() {
        GlobalScope.launch(Dispatchers.Default, CoroutineStart.DEFAULT) {
            try {
                val client = BCMWalletManager.provideHttpClient()
                val request = Request.Builder().url("$AMAZON_S3_STORAGE_ADDRESS/nodes-b.json").build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    isLastFetchError = false
                    val responseString = response.body()?.string() ?: ""
                    ALog.d(TAG, "updateConfig: $responseString")
                    prefs.edit().putString(PREFS_MYCELIUM_SERVERS, responseString).apply()

                    myceliumNodesResponse = Gson().fromJson(responseString, MyceliumNodesResponse::class.java)

                    electrumxServersChangedListener?.serverListChanged(getElectrumEndpoints())
                    normalServersChangedListener?.serverListChanged(getNormalApiEndPoints())


                } else {
                    isLastFetchError = true
                }

            } catch (_: Exception) {
            }
        }
    }

    // Returns the list of TcpEndpoint objects
    fun getElectrumEndpoints(): List<TcpEndpoint> {
        val isTestNet = AppUtil.useDevBlockChain()
        val pointList = mutableListOf<TcpEndpoint>()
        try {
            val nodesResponse = myceliumNodesResponse
                    ?: Gson().fromJson(prefs.getString(PREFS_MYCELIUM_SERVERS, ""), MyceliumNodesResponse::class.java)
            if (nodesResponse != null) {
                val electrumx = if (isTestNet) {
                    nodesResponse.btcTestnet.electrumx
                } else {
                    nodesResponse.btcMainnet.electrumx
                }
                electrumx.primary.forEach {
                    val strs = it.url.replace(TCP_TLS_PREFIX, "").split(":")
                    pointList.add(TcpEndpoint(strs[0], strs[1].toInt(), it.cert))
                }
                electrumx.backup.forEach {
                    val strs = it.url.replace(TCP_TLS_PREFIX, "").split(":")
                    pointList.add(TcpEndpoint(strs[0], strs[1].toInt(), it.cert))
                }

            }
        } catch (_: Exception) {
        }

        if (pointList.isEmpty()) {
            if (isTestNet) {
                testnetElectrumxEndpoints.forEach {
                    val strs = it.replace(TCP_TLS_PREFIX, "").split(":")
                    pointList.add(TcpEndpoint(strs[0], strs[1].toInt(), ""))
                }
            } else {
                prodnetElectrumxEndpoints.forEach {
                    val strs = it.replace(TCP_TLS_PREFIX, "").split(":")
                    pointList.add(TcpEndpoint(strs[0], strs[1].toInt(), ""))
                }
            }
        }
        return pointList
    }

    fun getNormalApiEndPoints(): ServerEndpoints {
        val isTestNet = AppUtil.useDevBlockChain()
        val pointList = mutableListOf<HttpsEndpoint>()
        try {
            val nodesResponse = myceliumNodesResponse
                    ?: Gson().fromJson(prefs.getString(PREFS_MYCELIUM_SERVERS, ""), MyceliumNodesResponse::class.java)
            if (nodesResponse != null) {
                val normal = if (isTestNet) {
                    nodesResponse.btcTestnet.normal
                } else {
                    nodesResponse.btcMainnet.normal
                }
                normal.primary.forEach {
                    pointList.add(HttpsEndpoint(it.url, it.cert))
                }
                normal.backup.forEach {
                    pointList.add(HttpsEndpoint(it.url, it.cert))
                }

            }

        } catch (_: Exception) {
        }

        if (pointList.isEmpty()) {
            pointList.addAll(if (isTestNet) testnetWapiEndpoints else prodnetWapiEndpoints)
        }
        return ServerEndpoints(pointList.toTypedArray())
    }


}