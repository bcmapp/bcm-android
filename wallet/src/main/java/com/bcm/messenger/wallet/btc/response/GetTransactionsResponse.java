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

import com.bcm.messenger.utility.proguard.NotGuard;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Collection;

public class GetTransactionsResponse implements Serializable, NotGuard {
    private static final long serialVersionUID = 1L;

    @JsonProperty
    public final Collection<TransactionX> transactions;

    public GetTransactionsResponse(@JsonProperty("transactions") Collection<TransactionX> transactions) {
        this.transactions = transactions;
    }

    public class TransactionX implements Serializable, NotGuard {

        public String txid;
        public String hash;
        public String blockHash;
        public long blockTime;
        public int confirmations;
        public String hex;
        public int time;
        public int height;

        @SerializedName("unconf_chain")
        public int unconfChain;
        @SerializedName("rbf")
        public boolean rbf;
        public String binary;

    }
}
