package org.whispersystems.signalservice.internal.push;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.internal.util.Base64;

import java.lang.reflect.Type;

public class IdentityKeyAdapter implements JsonSerializer<IdentityKey>, JsonDeserializer<IdentityKey> {

  @Override
  public IdentityKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    if (null == json) {
      return null;
    }

    try {
      String jsonString = json.getAsString();
      return new IdentityKey(Base64.decodeWithoutPadding(jsonString), 0);
    } catch (Throwable e) {
      throw new JsonParseException(e);
    }
  }

  @Override
  public JsonElement serialize(IdentityKey src, Type typeOfSrc, JsonSerializationContext context) {
    if (null == src) {
      return null;
    }
    return new JsonPrimitive(Base64.encodeBytesWithoutPadding(src.serialize()));
  }

}
