package com.bcm.messenger.wallet.utils.cache

import android.os.Parcel
import android.os.Parcelable
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

/**
 * @author Aaron
 * @email aaron@magicwindow.cn
 * @date 10/03/2018 17:00
 * @description
 */
interface CacheApi {
    fun put(key: String, value: String)

    fun put(key: String, value: String, saveTime: Int)

    fun getString(key: String): String?

    fun put(key: String, value: JSONObject)

    fun put(key: String, value: JSONObject, saveTime: Int)

    fun getJSONObject(key: String): JSONObject?

    fun put(key: String, value: JSONArray)

    fun put(key: String, value: JSONArray, saveTime: Int)

    fun getJSONArray(key: String): JSONArray?

    fun put(key: String, value: ByteArray)

    fun put(key: String, value: ByteArray, saveTime: Int)

    fun getBytes(key: String): ByteArray?

//    fun put(key: String, value: Serializable)

    fun put(key: String, value: Serializable, saveTime: Int = -1)

    fun getObject(key: String): Any?

//    fun put(key: String, value: Parcelable)

    fun put(key: String, value: Parcelable, saveTime: Int = -1)

    fun getParcelObject(key: String): Parcel?

    fun <T> getObject(key: String, creator: Parcelable.Creator<T>): T?

    fun remove(key: String): Boolean

    fun clear()
}