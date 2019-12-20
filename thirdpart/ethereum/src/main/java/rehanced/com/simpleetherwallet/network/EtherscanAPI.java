package rehanced.com.simpleetherwallet.network;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import rehanced.com.simpleetherwallet.interfaces.LastIconLoaded;
import rehanced.com.simpleetherwallet.interfaces.StorableWallet;
import rehanced.com.simpleetherwallet.utils.RequestCache;
import rehanced.com.simpleetherwallet.utils.TokenIconCache;

@Deprecated
public class EtherscanAPI {

    private static final String OFFICIAL_HOST = "http://api.etherscan.io/";
    private static final String RINKEBY_HOST = "http://rinkeby.etherscan.io/";

    private String token;
    private String currentHost;//当前域名

    private static EtherscanAPI instance;

    public static EtherscanAPI getInstance() {
        if (instance == null) {
            instance = new EtherscanAPI();
        }
        return instance;
    }

    /**
     * transfer network id related to
     *
     * @return
     */
    public static Byte getChainId() {
        //        if(BuildConfig.DEBUG) {
        //            return (byte)3;
        //        }else {
        //            return (byte)1;
        //        }
        return (byte) 1;
    }

    public void getPriceChart(long starttime, int period, boolean usd, Callback b) throws IOException {
        get(true, "http://poloniex.com/public?command=returnChartData&currencyPair=" + (usd ? "USDT_ETH" : "BTC_ETH") + "&start=" + starttime + "&end=9999999999&period=" + period, b);
    }


    /**
     * Retrieve all internal transactions from address like contract calls, for normal transactions @see rehanced.com.simpleetherwallet.network.EtherscanAPI#getNormalTransactions() )
     *
     * @param address Ether address
     * @param b       Network callback to @see rehanced.com.simpleetherwallet.fragments.FragmentTransactions#update() or @see rehanced.com.simpleetherwallet.fragments.FragmentTransactionsAll#update()
     * @param force   Whether to force (true) a network call or use cache (false). Only true if user uses swiperefreshlayout
     * @throws IOException Network exceptions
     */
    public void getInternalTransactions(String address, Callback b, boolean force) throws IOException {
        if (!force && RequestCache.getInstance().contains(RequestCache.TYPE_TXS_INTERNAL, address)) {
            b.onResponse(null, new Response.Builder().code(200).message("").request(new Request.Builder()
                    .url("api?module=account&action=txlistinternal&address=" + address + "&startblock=0&endblock=99999999&sort=asc&apikey=" + token)
                    .build()).protocol(Protocol.HTTP_1_0).body(ResponseBody.create(MediaType.parse("JSON"), RequestCache.getInstance().get(RequestCache.TYPE_TXS_INTERNAL, address))).build());
            return;
        }
        get("api?module=account&action=txlistinternal&address=" + address + "&startblock=0&endblock=99999999&sort=asc&apikey=" + token, b);
    }


    /**
     * Retrieve all normal ether transactions from address (excluding contract calls etc, @see rehanced.com.simpleetherwallet.network.EtherscanAPI#getInternalTransactions() )
     *
     * @param address Ether address
     * @param b       Network callback to @see rehanced.com.simpleetherwallet.fragments.FragmentTransactions#update() or @see rehanced.com.simpleetherwallet.fragments.FragmentTransactionsAll#update()
     * @param force   Whether to force (true) a network call or use cache (false). Only true if user uses swiperefreshlayout
     * @throws IOException Network exceptions
     */
    public void getNormalTransactions(String address, Callback b, boolean force) throws IOException {
        if (!force && RequestCache.getInstance().contains(RequestCache.TYPE_TXS_NORMAL, address)) {
            b.onResponse(null, new Response.Builder().code(200).message("").request(new Request.Builder()
                    .url("api?module=account&action=txlist&address=" + address + "&startblock=0&endblock=99999999&sort=asc&apikey=" + token)
                    .build()).protocol(Protocol.HTTP_1_0).body(ResponseBody.create(MediaType.parse("JSON"), RequestCache.getInstance().get(RequestCache.TYPE_TXS_NORMAL, address))).build());
            return;
        }
        get("api?module=account&action=txlist&address=" + address + "&startblock=0&endblock=99999999&sort=asc&apikey=" + token, b);
    }


    public void getEtherPrice(Callback b) throws IOException {
        get("api?module=stats&action=ethprice&apikey=" + token, b);
    }


    public void getGasPrice(Callback b) throws IOException {
        get("api?module=proxy&action=eth_gasPrice&apikey=" + token, b);
    }


    /**
     * Get token balances via ethplorer.io
     *
     * @param address Ether address
     * @param b       Network callback to @see rehanced.com.simpleetherwallet.fragments.FragmentDetailOverview#update()
     * @param force   Whether to force (true) a network call or use cache (false). Only true if user uses swiperefreshlayout
     * @throws IOException Network exceptions
     */
    public void getTokenBalances(String address, Callback b, boolean force) throws IOException {
        if (!force && RequestCache.getInstance().contains(RequestCache.TYPE_TOKEN, address)) {
            b.onResponse(null, new Response.Builder().code(200).message("").request(new Request.Builder()
                    .url("https://api.ethplorer.io/getAddressInfo/" + address + "?apiKey=freekey")
                    .build()).protocol(Protocol.HTTP_1_0).body(ResponseBody.create(MediaType.parse("JSON"), RequestCache.getInstance().get(RequestCache.TYPE_TOKEN, address))).build());
            return;
        }
        get("http://api.ethplorer.io/getAddressInfo/" + address + "?apiKey=freekey", b);
    }


    /**
     * Download and save token icon in permanent image cache (TokenIconCache)
     *
     * @param c         Application context, used to load TokenIconCache if reinstanced
     * @param tokenName Name of token
     * @param lastToken Boolean defining whether this is the last icon to download or not. If so callback is called to refresh recyclerview (notifyDataSetChanged)
     * @param callback  Callback to @see rehanced.com.simpleetherwallet.fragments.FragmentDetailOverview#onLastIconDownloaded()
     * @throws IOException Network exceptions
     */
    public void loadTokenIcon(final Context c, String tokenName, final boolean lastToken, final LastIconLoaded callback) throws IOException {
        if (tokenName.indexOf(" ") > 0) {
            tokenName = tokenName.substring(0, tokenName.indexOf(" "));
        }
        if (TokenIconCache.getInstance(c).contains(tokenName)) {
            return;
        }

        if (tokenName.equalsIgnoreCase("OMGToken")) {
            tokenName = "omise";
        } else if (tokenName.equalsIgnoreCase("0x")) {
            tokenName = "0xtoken_28";
        }

        final String tokenNamef = tokenName;
        get(true, "http://etherscan.io//token/images/" + tokenNamef + ".PNG", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (c == null) {
                    return;
                }
                ResponseBody in = response.body();
                InputStream inputStream = in.byteStream();
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                final Bitmap bitmap = BitmapFactory.decodeStream(bufferedInputStream);
                TokenIconCache.getInstance(c).put(c, tokenNamef, new BitmapDrawable(c.getResources(), bitmap).getBitmap());
                // if(lastToken) // TODO: resolve race condition
                callback.onLastIconDownloaded();
            }
        });
    }


    public void getGasLimitEstimate(String to, Callback b) throws IOException {
        get("api?module=proxy&action=eth_estimateGas&to=" + to + "&value=0xff22&gasPrice=0x051da038cc&gas=0xffffff&apikey=" + token, b);
    }


    public void getBalance(String address, Callback b) throws IOException {
        get("api?module=account&action=balance&address=" + address + "&apikey=" + token, b);
    }


    public void getNonceForAddress(String address, Callback b) throws IOException {
        get("api?module=proxy&action=eth_getTransactionCount&address=" + address + "&tag=latest&apikey=" + token, b);
    }


    public void getPriceConversionRates(String currencyConversion, Callback b) throws IOException {
        get(true, "https://api.fixer.io/latest?base=USD&symbols=" + currencyConversion, b);
    }


    public void getBalances(List<StorableWallet> addresses, Callback b) throws IOException {
        StringBuilder urlBuilder = new StringBuilder("api?module=account&action=balancemulti&address=");
        for (StorableWallet address : addresses) {
            urlBuilder.append(address.getPubKey());
            urlBuilder.append(",");
        }
        if (!addresses.isEmpty()) {
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }
        // remove last , AND add token
        urlBuilder.append("&tag=latest&apikey=");
        urlBuilder.append(token);
        get(urlBuilder.toString(), b);
    }


    public void forwardTransaction(String raw, Callback b) throws IOException {
        get("api?module=proxy&action=eth_sendRawTransaction&hex=" + raw + "&apikey=" + token, b);
    }


    public void get(String url, Callback b) throws IOException {
        get(false, url, b);
    }

    public void get(boolean useFullUrl, String url, Callback b) throws IOException {
        Request request = new Request.Builder()
                .url(useFullUrl ? url : currentHost + url)
                .build();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        client.newCall(request).enqueue(b);
    }

    private EtherscanAPI() {
        token = "UGDGCQ1J92XXTUYKFEMEZWJZZFCNTP9A54";
        //        currentHost = BuildConfig.DEBUG ? RINKEBY_HOST : OFFICIAL_HOST;
        currentHost = OFFICIAL_HOST;
    }

}
