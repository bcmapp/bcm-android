package com.bcm.messenger.utility.logger

import com.orhanobut.logger.FormatStrategy
import com.orhanobut.logger.LogStrategy
import com.orhanobut.logger.Logger
import java.text.SimpleDateFormat
import java.util.*

/**
 * 输出到文本的日志格式
 * @author ling
 */
class TextFormatStrategy(builder: Builder) : FormatStrategy {

    private var date: Date?
    private var dateFormat: SimpleDateFormat?
    private var logStrategy: LogStrategy?

    init {
        date = builder.date
        dateFormat = builder.dateFormat
        logStrategy = builder.logStrategy
    }

    override fun log(priority: Int, onceOnlyTag: String?, message: String?) {
        val logStrategy = this.logStrategy ?: return

        date?.time = System.currentTimeMillis()

        val builder = StringBuilder()

        // human-readable date/time
        builder.append(SEPARATOR)
        builder.append(dateFormat?.format(date) ?: "")

        // level
        builder.append(SEPARATOR)
        builder.append(logLevel(priority))

        // tag
        if (onceOnlyTag?.isNotEmpty() == true) {
            builder.append(SEPARATOR)
            builder.append(onceOnlyTag)
        }

        builder.append(SEPARATOR)
        builder.append(message)

        // new line
        builder.append(NEW_LINE)

        logStrategy.log(priority,"", builder.toString())
    }

    class Builder {

        internal var date: Date? = null
        internal var dateFormat: SimpleDateFormat? = null
        internal var logStrategy: LogStrategy? = null

        fun date(date: Date): Builder {
            this.date = date
            return this
        }

        fun dateFormat(timeFormat: SimpleDateFormat): Builder {
            dateFormat = timeFormat
            return this
        }

        fun logStrategy(logStrategy: LogStrategy?): Builder {
            this.logStrategy = logStrategy
            return this
        }

        fun build(): TextFormatStrategy {
            if (date == null) {
                date = Date()
            }
            if (dateFormat == null) {
                dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS", Locale.UK)
            }
            return TextFormatStrategy(this)
        }

    }

    companion object {
        internal val NEW_LINE = System.getProperty("line.separator")
        internal const val SEPARATOR = ","

        fun newBuilder(): Builder {
            return Builder()
        }

        internal fun logLevel(value: Int): String {
            return when (value) {
                Logger.VERBOSE -> "VERBOSE"
                Logger.DEBUG -> "DEBUG"
                Logger.INFO -> "INFO"
                Logger.WARN -> "WARN"
                Logger.ERROR -> "ERROR"
                Logger.ASSERT -> "ASSERT"
                else -> "UNKNOWN"
            }
        }
    }
}