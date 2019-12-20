package com.bcm.messenger.me.ui.language

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.*

/**
 * Created by zjl on 2018/10/8.
 */
class LanguageViewModel : ViewModel() {

    companion object {
        fun getDisplayName(systemLanguage: String): String {
            return when(systemLanguage) {
                Locale.ENGLISH.language -> "English"
                Locale.SIMPLIFIED_CHINESE.language -> "简体中文"
                "ar" -> "العربية"
                "hi" -> "हिन्दी"
                "ru" -> "русский"
                "fa" -> "فارسی"
                "ja" -> "日本語"
                else -> "Unknown"
            }
        }

        fun getDisplayLanguageList(): List<LanguageSelectBean> {
            return listOf(LanguageSelectBean("English", "English", Locale.ENGLISH.country, Locale.ENGLISH.language),
                    LanguageSelectBean("简体中文", "Chinese,Simplified", Locale.SIMPLIFIED_CHINESE.country, Locale.SIMPLIFIED_CHINESE.language),
                    LanguageSelectBean("العربية", "Arabic", "SA", "ar"),
                    LanguageSelectBean("हिन्दी", "Hindi", "IN", "hi"),
                    LanguageSelectBean("русский", "Russian", "RU", "ru"),
                    LanguageSelectBean("فارسی", "Persian", "IR", "fa"),
                    LanguageSelectBean("日本語", "Japanese", "JP", "ja"))
        }
    }

    val languageName: MutableLiveData<String> = MutableLiveData()
    val countryName: MutableLiveData<String> = MutableLiveData()
}