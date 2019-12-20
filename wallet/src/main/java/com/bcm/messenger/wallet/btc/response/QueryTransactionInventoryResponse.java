/*
 * Copyright 2013, 2014 Megion Research & Development GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bcm.messenger.wallet.btc.response;

import androidx.annotation.NonNull;
import com.bcm.messenger.utility.proguard.NotGuard;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class QueryTransactionInventoryResponse implements Serializable, NotGuard {
    private static final long serialVersionUID = 1L;

    @JsonProperty
    public final int height;
    @JsonProperty
    public final List<TransactionHistoryInfo> txHistory;

    public final List<String> txIds = new ArrayList<String>(0);

    public QueryTransactionInventoryResponse(@JsonProperty("height") int height, List<TransactionHistoryInfo> txHistory) {
        this.height = height;
        this.txHistory = txHistory;
    }

    public static class TransactionHistoryInfo implements Comparable<TransactionHistoryInfo>, NotGuard {

        @SerializedName("fee")
        public int fee;
        @SerializedName("height")
        public int height;
        @SerializedName("tx_hash")
        public String txHash;

        @Override
        public int compareTo(@NonNull TransactionHistoryInfo o) {
            int myHeight = height;
            if (height == -1) {
                myHeight = Integer.MAX_VALUE;
            }
            int otherHeight = o.height;
            if (o.height == -1) {
                otherHeight = Integer.MAX_VALUE;
            }
            return Integer.compare(otherHeight, myHeight);
        }
    }

}
