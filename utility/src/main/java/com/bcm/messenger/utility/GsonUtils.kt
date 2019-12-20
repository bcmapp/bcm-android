package com.bcm.messenger.utility

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.lang.reflect.Type

/**
 * Created by zjl on 2018/8/21.
 */
object GsonUtils {
    val gson = Gson()

    @Throws(JsonParseException::class)
    fun <T> fromJson(o: String, t: Type): T {
        return gson.fromJson(o, t)
    }

    @Throws(JsonParseException::class)
    fun <T> fromJson(o: String, c: Class<T>): T {
        return gson.fromJson(o, c)
    }

    @Throws(JsonParseException::class)
    fun <T> fromJson(serialized: ByteArray, clazz: Class<T>): T {
        return fromJson(String(serialized), clazz)
    }

    @Throws(JsonParseException::class)
    fun <T> fromJson(serialized: InputStream, clazz: Class<T>): T {
        return gson.fromJson<T>(InputStreamReader(serialized), clazz)
    }

    @Throws(JsonParseException::class)
    fun <T> fromJson(serialized: Reader, clazz: Class<T>): T {
        return gson.fromJson<T>(serialized, clazz)
    }


    fun toJson(o: Any): String {
        return gson.toJson(o)
    }
}