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

package com.bcm.messenger.wallet.btc.request;

import com.bcm.messenger.utility.proguard.NotGuard;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.bitcoinj.core.Address;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Collection;

public class QueryTransactionInventoryRequest implements Serializable, NotGuard {
    private static final long serialVersionUID = 1L;

    @JsonProperty
    public final int version;
    /**
     * The list of addresses to query
     */
    @JsonProperty
    public final Collection<Address> addresses;

    public final int limit = 1000;

    public QueryTransactionInventoryRequest(@JsonProperty("version") int version,
                                            @JsonProperty("addresses") Collection<Address> addresses) {
        this.version = version;
        this.addresses = addresses;
    }

    public String toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("version", version);
            JSONArray array = new JSONArray();
            for (Address address : addresses) {
                array.put(address.toString());
            }
            json.put("addresses", array);
            json.put("limit", limit);
            return json.toString();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    @Override
    public String toString() {
        return toJson();
    }
}
