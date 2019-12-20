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
    private var tag: String

    init {
        date = builder.date
        dateFormat = builder.dateFormat
        logStrategy = builder.logStrategy
        tag = builder.tag
    }

    override fun log(priority: Int, onceOnlyTag: String?, message: String?) {
        val logStrategy = this.logStrategy ?: return

        val tag = formatTag(onceOnlyTag)

        date?.time = System.currentTimeMillis()

        val builder = StringBuilder()

        // machine-readable date/time
        builder.append(date?.time?.toString())

        // human-readable date/time
        builder.append(SEPARATOR)
        builder.append(dateFormat?.format(date) ?: "")

        // level
        builder.append(SEPARATOR)
        builder.append(logLevel(priority))

        // tag
        builder.append(SEPARATOR)
        builder.append(tag)

        builder.append(SEPARATOR)
        builder.append(message)

        // new line
        builder.append(NEW_LINE)

        logStrategy.log(priority, tag, builder.toString())
    }

    private fun formatTag(tag: String?): String {
        return if (!tag.isNullOrEmpty() && tag != this.tag) {
            this.tag + "_" + tag
        } else this.tag
    }

    class Builder {

        internal var date: Date? = null
        internal var dateFormat: SimpleDateFormat? = null
        internal var logStrategy: LogStrategy? = null
        internal var tag = "PRETTY_LOGGER"

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

        fun tag(tag: String): Builder {
            this.tag = tag
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
            when (value) {
                Logger.VERBOSE -> return "VERBOSE"
                Logger.DEBUG -> return "DEBUG"
                Logger.INFO -> return "INFO"
                Logger.WARN -> return "WARN"
                Logger.ERROR -> return "ERROR"
                Logger.ASSERT -> return "ASSERT"
                else -> return "UNKNOWN"
            }
        }
    }
}