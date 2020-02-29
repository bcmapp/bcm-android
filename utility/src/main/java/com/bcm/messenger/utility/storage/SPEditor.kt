package com.bcm.messenger.utility.storage

import android.content.Context
import android.content.SharedPreferences
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.google.gson.reflect.TypeToken


@Suppress("UNCHECKED_CAST")
class SPEditor(private val spName:String, private val mode:Int = Context.MODE_PRIVATE) {
    fun <V:Any>set(key:String, value:V) {
        val sp:SharedPreferences = AppContextHolder.APP_CONTEXT.getSharedPreferences(spName, mode)
        val editor = sp.edit()
        when(value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Double -> editor.putLong(key, java.lang.Double.doubleToLongBits(value))
            is Long -> editor.putLong(key, value)
            else -> editor.putString(key, GsonUtils.toJson(value))
        }
        editor.apply()
    }

    fun <V:Any>get(key:String, default:V): V {
        val sp:SharedPreferences = AppContextHolder.APP_CONTEXT.getSharedPreferences(spName, mode)
        when(default) {
            is String -> return sp.getString(key, default) as V
            is Int -> return sp.getInt(key, default) as V
            is Boolean -> return sp.getBoolean(key, default) as V
            is Float -> return sp.getFloat(key, default) as V
            is Double -> return sp.getLong(key, java.lang.Double.doubleToLongBits(default)).toDouble() as V
            is Long -> return sp.getLong(key, default) as V
            else -> {
                val v = sp.getString(key, "")
                return if (v!!.isEmpty()) {
                    default
                } else {
                    try {
                        GsonUtils.fromJson<V>(v, object : TypeToken<V>(){}.type)
                    } catch (e:Throwable) {
                        default
                    }
                }
            }
        }
    }

    fun set(key:String, stringSet:MutableSet<String>) {
        val sp:SharedPreferences = AppContextHolder.APP_CONTEXT.getSharedPreferences(spName, mode)
        val editor = sp.edit()
        editor.putStringSet(key, stringSet.takeIf { it.isNotEmpty() }).apply()
    }

    fun get(key:String, default:MutableSet<String>):MutableSet<String> {
        val sp:SharedPreferences = AppContextHolder.APP_CONTEXT.getSharedPreferences(spName, mode)
        val set = sp.getStringSet(key, default) ?: return mutableSetOf()
        return LinkedHashSet(set)
    }

    fun remove(key:String) {
        val sp:SharedPreferences = AppContextHolder.APP_CONTEXT.getSharedPreferences(spName, mode)
        sp.edit().remove(key).apply()
    }
}