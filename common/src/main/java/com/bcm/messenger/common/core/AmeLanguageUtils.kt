package com.bcm.messenger.common.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.utility.AppContextHolder
import java.util.*

/**
 * Created by Kin on 2018/11/29
 */
private var country: String? = null
private var language: String? = null

fun getSelectedLocale(context: Context?): Locale {
    context ?: return Locale.getDefault()
    if (country == null || language == null) {
        country = SuperPreferences.getCountryString(context, Locale.getDefault().country)
        language = SuperPreferences.getLanguageString(context, Locale.getDefault().language)
    }
    return Locale(language, country)
}

fun setLocale(context: Context?): Context? {
    if (context != null) {
        val newLocale = getSelectedLocale(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val config = context.resources.configuration
            config.setLocale(newLocale)
            return context.createConfigurationContext(config)
        } else {
            val resources = context.resources
            val config = resources.configuration
            config.locale = newLocale
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }
    return context
}

fun setApplicationLanguage(context: Context?, newConfig: Configuration?) {
    context ?: return
    val dm = context.applicationContext.resources.displayMetrics
    val config = context.applicationContext.resources.configuration
    val locale = getSelectedLocale(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)
        config.locales = localeList
        config.setLocale(locale)
        if (newConfig != null) {
            config.uiMode = newConfig.uiMode
        }
        context.applicationContext.createConfigurationContext(config)
        Locale.setDefault(locale)
    } else {
        config.locale = locale
        Locale.setDefault(locale)
    }
    context.applicationContext.resources.updateConfiguration(config, dm)
    
    SystemUtils.setUseLanguage(locale.language ?: language)
}

fun onConfigurationChanged(context: Context?, newConfig: Configuration?) {
    context ?: return
    setLocale(context)
    setApplicationLanguage(context, newConfig)
}

fun updateLanguage(context: Context?, newCountry: String?, newLanguage: String?) {
    context ?: return
    SuperPreferences.setLanguageString(context, newLanguage ?: language)
    SuperPreferences.setCountryString(context, newCountry ?: country)
    country = newCountry ?: country
    language = newLanguage ?: language
    setApplicationLanguage(AppContextHolder.APP_CONTEXT, null)
    
    SystemUtils.setUseLanguage(newLanguage ?: language)
}