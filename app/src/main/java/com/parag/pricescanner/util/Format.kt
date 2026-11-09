package com.parag.pricescanner.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val locale = Locale("en", "IN")
    private val currency = NumberFormat.getCurrencyInstance(locale).apply { maximumFractionDigits = 2 }
    private val currencyShort = NumberFormat.getCurrencyInstance(locale).apply { maximumFractionDigits = 0 }
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun money(v: Double): String = currency.format(v)
    fun moneyShort(v: Double): String = currencyShort.format(v)

    /** Compact amount for chart labels: ₹950, ₹1.2K, ₹3.4L. */
    fun compact(v: Double): String = when {
        v >= 1_00_00_000 -> "₹%.1fCr".format(v / 1_00_00_000)
        v >= 1_00_000 -> "₹%.1fL".format(v / 1_00_000)
        v >= 1_000 -> "₹%.1fK".format(v / 1_000)
        else -> "₹%.0f".format(v)
    }

    fun price(v: Double): String = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.2f".format(v)

    private val dayHeader = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)
    private val dayShort = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dayFull = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val time = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val month = DateTimeFormatter.ofPattern("MMM", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEE", locale)
    private val stamp = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", locale)
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", locale)

    fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun dateTime(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    fun startOf(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayHeader(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "Today · " + dayShort.format(d)
            today.minusDays(1) -> "Yesterday · " + dayShort.format(d)
            else -> dayHeader.format(d)
        }
    }
    fun dayShort(d: LocalDate): String = dayShort.format(d)
    fun dayFull(d: LocalDate): String = dayFull.format(d)
    fun epochDay(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    /** Days until expiry (negative when expired). */
    fun daysLeft(exp: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), exp)

    fun expiryText(exp: LocalDate): String {
        val n = daysLeft(exp)
        return when {
            n < 0 -> "Expired " + dayFull(exp)
            n == 0L -> "Expires today"
            n <= 30 -> "Expires in $n day" + (if (n == 1L) "" else "s")
            else -> "Exp " + dayFull(exp)
        }
    }
    fun time(millis: Long): String = time.format(dateTime(millis))
    fun month(d: LocalDate): String = month.format(d)
    fun monthYear(d: LocalDate): String = monthYear.format(d)
    fun weekday(d: LocalDate): String = weekday.format(d)
    fun stamp(t: LocalDateTime): String = stamp.format(t)
    fun fileStamp(t: LocalDateTime): String = fileStamp.format(t)
}
