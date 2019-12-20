package com.bcm.messenger.wallet.utils.cache

import android.os.Parcel
import android.os.Parcelable
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

/**
 * @author Aaron
 * @email aaron@magicwindow.cn
 * @date 10/03/2018 19:20
 * @description
 */
class CacheEmpty : CacheApi {
    override fun getObject(key: String): Any? {
        return null
    }

    fun put(key: String, value: Parcelable) {

    }

    override fun put(key: String, value: Parcelable, saveTime: Int) {

    }

    override fun getParcelObject(key: String): Parcel? {
        return null
    }

    override fun <T> getObject(key: String, creator: Parcelable.Creator<T>): T? {
        return null
    }

    override fun remove(key: String): Boolean {
        return true
    }

    override fun clear() {

    }

    override fun put(key: String, value: String) {

    }

    override fun put(key: String, value: String, saveTime: Int) {

    }

    override fun getString(key: String): String? {
        return null
    }

    override fun put(key: String, value: JSONObject) {

    }

    override fun put(key: String, value: JSONObject, saveTime: Int) {

    }

    override fun getJSONObject(key: String): JSONObject? {
        return null
    }

    override fun put(key: String, value: JSONArray) {

    }

    override fun put(key: String, value: JSONArray, saveTime: Int) {

    }

    override fun getJSONArray(key: String): JSONArray? {
        return null
    }

    override fun put(key: String, value: ByteArray) {

    }

    override fun put(key: String, value: ByteArray, saveTime: Int) {

    }

    override fun getBytes(key: String): ByteArray {
        return ByteArray(0)
    }
//
//    fun put(key: String, value: Serializable) {
//
//    }

    override fun put(key: String, value: Serializable, saveTime: Int) {

    }
}