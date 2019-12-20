package com.bcm.messenger.wallet.btc.request;

import com.bcm.messenger.utility.proguard.NotGuard;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.Serializable;

/**
 * Created by wjh on 2019/3/26
 */
public class BroadcastTransactionBase64Request implements Serializable, NotGuard {

    private static final long serialVersionUID = 1L;

    @JsonProperty
    public final int version;
    @JsonProperty
    public final String rawTransaction;

    public BroadcastTransactionBase64Request(@JsonProperty("version") int version,
                                             @JsonProperty("rawTransaction") String rawTransaction) {
        this.version = version;
        this.rawTransaction = rawTransaction;
    }

    public String toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("version", version);
            JSONArray array = new JSONArray();
            json.put("rawTransaction", rawTransaction);
            return json.toString();

        } catch (Exception ex) {

        }
        return "";
    }

    @Override
    public String toString() {
        return toJson();
    }
}
