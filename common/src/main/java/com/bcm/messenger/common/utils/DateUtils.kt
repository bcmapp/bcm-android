package com.bcm.messenger.common.utils

import android.content.Context
import com.bcm.messenger.common.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 
 * Created by wjh on 2018/4/17
 */
object DateUtils {

    /**
     * （：x:xx)
     */
    fun convertMinuteAndSecond(time: Long): String {
        val hour = time / 3600000
        val min = (time % 3600000) / 60000
        val second = (time % 60000) / 1000

        return if (hour > 0) {
            String.format("%d:%02d:%02d", hour, min, second)
        } else {
            String.format("%d:%02d", min, second)
        }
    }

    /**
     * （：xx:xx)
     */
    fun convertTimingMinuteAndSecond(time: Long): String {
        val hour = time / 3600000
        val min = (time % 3600000) / 60000
        val second = (time % 60000) / 1000

        return if (hour > 0) {
            String.format("%02d:%02d:%02d", hour, min, second)
        } else {
            String.format("%02d:%02d", min, second)
        }
    }

    fun getFormattedDateTime(time: Long, template: String, locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat(template, locale).format(Date(time))
    }

    /**
     * 
     */
    fun formatHourTime(time: Long): String {
        return getFormattedDateTime(time, "HH:mm", Locale.getDefault())
    }

    /**
     * 
     */
    fun formatDefaultTime(time: Long): String {
        return getFormattedDateTime(time, "yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    fun formatFileTime(time: Long): String {
        return getFormattedDateTime(time, "dd-MM-yyyy hh:mm aa", Locale.getDefault())
    }

    fun formatMonthTime(time: Long): String {
        return getFormattedDateTime(time, "MM-dd HH:mm", Locale.getDefault())
    }

    fun formatDayTime(secTime: Long): String {
        return formatDayTimeForMillisecond(secTime * 1000)
    }

    fun formatDayTimeForMillisecond(millisecondTime: Long): String {
        return getFormattedDateTime(millisecondTime, "yyyy-MM-dd", Locale.getDefault())
    }

    fun isYesterday(`when`: Long): Boolean {
        return android.text.format.DateUtils.isToday(`when` + TimeUnit.DAYS.toMillis(1))
    }

    private fun isWithin(millis: Long, span: Long, unit: TimeUnit): Boolean {
        return System.currentTimeMillis() - millis <= unit.toMillis(span)
    }

    /**
     * 
     */
    fun getNoteTimSpan(context: Context, timestamp: Long, locale: Locale): String {
        return if (!AppUtil.is24HourFormat(context)) {
            if (locale.language == Locale.CHINESE.language) {
                getFormattedDateTime(timestamp, "yyyyMd hh:mm", locale) + " ${getPeriodText(context, locale, timestamp)}"
            }else {
                getFormattedDateTime(timestamp, "d MMM yyyy hh:mm", locale) + " ${getPeriodText(context, locale, timestamp)}"
            }
        } else {
            if (locale.language == Locale.CHINESE.language) {
                getFormattedDateTime(timestamp, "yyyyMd HH:mm", locale)
            }else {
                getFormattedDateTime(timestamp, "d MMM yyyy HH:mm", locale)
            }
        }
    }

    /**
     * 
     */
    fun getConversationTimeSpan(context: Context, timestamp: Long, locale: Locale): String {
        return if (!AppUtil.is24HourFormat(context)) {
            when {
                android.text.format.DateUtils.isToday(timestamp) -> getFormattedDateTime(timestamp, "hh:mm", locale) + " ${getPeriodText(context, locale, timestamp)}"
                isYesterday(timestamp) -> context.getString(R.string.common_yesterday_text) + " ${getFormattedDateTime(timestamp, "hh:mm", locale)} ${getPeriodText(context, locale, timestamp)}"
                isWithin(timestamp, 7, TimeUnit.DAYS) -> getFormattedDateTime(timestamp, "EEE, hh:mm", locale)
                else -> getFormattedDateTime(timestamp, "MMM d, yyyy hh:mm", locale)
            }
        } else {
            when {
                android.text.format.DateUtils.isToday(timestamp) -> getFormattedDateTime(timestamp, "HH:mm", locale)
                isYesterday(timestamp) -> context.getString(R.string.common_yesterday_text) + " ${getFormattedDateTime(timestamp, "HH:mm", locale)}"
                isWithin(timestamp, 7, TimeUnit.DAYS) -> getFormattedDateTime(timestamp, "EEE, HH:mm", locale)
                else -> getFormattedDateTime(timestamp, "MMM d, yyyy HH:mm", locale)
            }
        }
    }

    /**
     * 
     */
    fun getThreadMessageTimeSpan(context: Context, timestamp: Long, locale: Locale): String {
        return if (!AppUtil.is24HourFormat(context)) {
            when {
                android.text.format.DateUtils.isToday(timestamp) -> getFormattedDateTime(timestamp, "hh:mm", locale) + " ${getPeriodText(context, locale, timestamp)}"
                isYesterday(timestamp) -> context.getString(R.string.common_yesterday_text)
                isWithin(timestamp, 7, TimeUnit.DAYS) -> getFormattedDateTime(timestamp, "EE", locale)
                else -> getFormattedDateTime(timestamp, "yyyy/MM/d", locale)
            }
        } else {
            when {
                android.text.format.DateUtils.isToday(timestamp) -> getFormattedDateTime(timestamp, "HH:mm", locale)
                isYesterday(timestamp) -> context.getString(R.string.common_yesterday_text)
                isWithin(timestamp, 7, TimeUnit.DAYS) -> getFormattedDateTime(timestamp, "EE", locale)
                else -> getFormattedDateTime(timestamp, "yyyy/MM/d", locale)
            }
        }
    }

    private fun getPeriodText(context: Context, locale: Locale, timestamp: Long): String {
        val calendar = Calendar.getInstance(locale)
        calendar.timeInMillis = timestamp
        return if (calendar.get(Calendar.HOUR_OF_DAY) >= 12) context.getString(R.string.common_afternoon_text) else context.getString(R.string.common_morning_text)
    }
}