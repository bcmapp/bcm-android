/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 * <p>
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;

import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.util.Base64;

import java.lang.reflect.Type;

public class PreKeyEntity {
    private int keyId;
    @JsonAdapter(ECPublicKeyTypeAdapter.class)
    private ECPublicKey publicKey;

    public PreKeyEntity() {
    }

    public PreKeyEntity(int keyId, ECPublicKey publicKey) {
        this.keyId = keyId;
        this.publicKey = publicKey;
    }

    public int getKeyId() {
        return keyId;
    }

    public ECPublicKey getPublicKey() {
        return publicKey;
    }

    class ECPublicKeyTypeAdapter implements JsonDeserializer<ECPublicKey>, JsonSerializer<ECPublicKey> {
        @Override
        public ECPublicKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (null != json) {
                try {
                    String publicSting = json.getAsString();
                    return Curve.decodePoint(Base64.decodeWithoutPadding(publicSting), 0);
                } catch (Throwable e) {
                    throw new JsonParseException("unknown public key", e);
                }
            }
            return null;
        }

        @Override
        public JsonElement serialize(ECPublicKey src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Base64.encodeBytesWithoutPadding(src.serialize()));
        }
    }
}
