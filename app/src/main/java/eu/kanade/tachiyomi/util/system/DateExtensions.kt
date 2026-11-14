package eu.kanade.tachiyomi.util.system

import android.text.format.DateUtils
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

fun Date.toTimestampString(dateFormatter: DateFormat): String {
    val date = dateFormatter.format(this)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(this)
    return "$date $time"
}

private val relativeDateCache = java.util.concurrent.ConcurrentHashMap<Long, String>()
@Volatile private var relativeDateCacheMinute = 0L

val Long.timeSpanFromNow: String
    get() {
        val minute = System.currentTimeMillis() / 60_000L
        if (minute != relativeDateCacheMinute) {
            relativeDateCache.clear()
            relativeDateCacheMinute = minute
        }
        if (relativeDateCache.size > 2000) relativeDateCache.clear()
        return relativeDateCache.getOrPut(this) {
            DateUtils.getRelativeTimeSpanString(this).toString()
        }
    }

fun SimpleDateFormat.tryParse(date: String?): Long {
    return try {
        when (date == null) {
            true -> 0L
            false -> this.parse(date)!!.time
        }
    } catch (_: ParseException) {
        0L
    }
}

fun Long.toLocalDate(): LocalDate {
    return LocalDate.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
}

fun Instant.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
    return LocalDate.ofInstant(this, zoneId)
}
