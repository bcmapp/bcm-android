/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push;

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.signalservice.internal.push.PreKeyEntity
import org.whispersystems.signalservice.internal.util.Base64
import java.lang.reflect.Type

class SignedPreKeyEntity : PreKeyEntity {
  @JsonAdapter(TypeAdapter::class)
  var signature: ByteArray? = null

  constructor() {}

  constructor(keyId: Int, publicKey: ECPublicKey, signature: ByteArray) : super(keyId, publicKey) {
    this.signature = signature
  }

  class TypeAdapter : JsonDeserializer<ByteArray>,JsonSerializer<ByteArray> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext): ByteArray? {
      if (null == json) {
        return null
      }

      return try {
        Base64.decodeWithoutPadding(json.asString)
      } catch (e:Throwable) {
        throw JsonParseException("unknown type with signature")
      }
    }

    override fun serialize(src: ByteArray?, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement? {
      if (null == src) {
        return null
      }

      return JsonPrimitive(Base64.encodeBytesWithoutPadding(src))
    }
  }
}
