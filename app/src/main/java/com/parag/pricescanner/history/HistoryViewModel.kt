package com.parag.pricescanner.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parag.pricescanner.data.Purchase
import com.parag.pricescanner.data.Repository
import com.parag.pricescanner.export.PdfExporter
import com.parag.pricescanner.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class Period(val label: String) { ALL("All"), TODAY("Today"), WEEK("This week"), MONTH("This month"), CUSTOM("Custom") }

data class Filter(
    val period: Period = Period.MONTH,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val query: String = "",
    val category: com.parag.pricescanner.scan.Category? = null,
) {
    /** Inclusive date bounds, or nulls for no bound. */
    fun bounds(today: LocalDate = LocalDate.now()): Pair<LocalDate?, LocalDate?> = when (period) {
        Period.ALL -> null to null
        Period.TODAY -> today to today
        Period.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to today
        Period.MONTH -> today.withDayOfMonth(1) to today
        Period.CUSTOM -> customStart to (customEnd ?: customStart)
    }

    fun label(): String {
        val (s, e) = bounds()
        return when {
            s == null -> "All records"
            s == e -> Fmt.dayFull(s)
            else -> Fmt.dayFull(s) + " – " + Fmt.dayFull(e ?: LocalDate.now())
        } + (if (category != null) " · ${category.label}" else "") + if (query.isNotBlank()) " · \"$query\"" else ""
    }
}

data class DayGroup(val date: LocalDate, val items: List<Purchase>) {
    val total get() = items.sumOf { it.total }
    val qty get() = items.sumOf { it.quantity }
}

data class HistoryState(
    val filter: Filter = Filter(),
    val groups: List<DayGroup> = emptyList(),
    val filtered: List<Purchase> = emptyList(),
    val loading: Boolean = true,
) {
    val total get() = filtered.sumOf { it.total }
    val qty get() = filtered.sumOf { it.quantity }
}

class HistoryViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    private val filter = MutableStateFlow(Filter())

    val productNames = repo.productNames

    val state: StateFlow<HistoryState> = combine(repo.purchases, filter) { all, f ->
        val (s, e) = f.bounds()
        val q = f.query.trim()
        val list = all.filter { p ->
            val d = Fmt.date(p.timestamp)
            (s == null || !d.isBefore(s)) && (e == null || !d.isAfter(e)) &&
                (q.isEmpty() || p.productName.contains(q, ignoreCase = true) || p.barcode?.contains(q) == true) &&
                (f.category == null || p.category == f.category.name)
        }
        HistoryState(
            filter = f,
            filtered = list,
            groups = list.groupBy { Fmt.date(it.timestamp) }.map { (d, l) -> DayGroup(d, l) }.sortedByDescending { it.date },
            loading = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun setPeriod(p: Period) { filter.value = filter.value.copy(period = p) }
    fun setCustom(start: LocalDate, end: LocalDate) {
        filter.value = filter.value.copy(period = Period.CUSTOM, customStart = start, customEnd = end)
    }
    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setCategory(c: com.parag.pricescanner.scan.Category?) { filter.value = filter.value.copy(category = c) }

    /** Keeps the original time of day when the date is changed. */
    private fun timestampFor(date: LocalDate, original: Long?): Long {
        val time = original?.let { Fmt.dateTime(it).toLocalTime() }
            ?: if (date == LocalDate.now()) java.time.LocalTime.now() else java.time.LocalTime.NOON
        return date.atTime(time).atZone(Fmt.zone).toInstant().toEpochMilli()
    }

    fun add(
        name: String, price: Double, qty: Int, date: LocalDate, mfg: LocalDate?, exp: LocalDate?,
        category: com.parag.pricescanner.scan.Category?,
    ) = viewModelScope.launch {
        repo.add(
            Purchase(
                productName = name, unitPrice = price, quantity = qty, timestamp = timestampFor(date, null),
                category = category?.name,
                mfgDate = mfg?.toEpochDay(), expiryDate = exp?.toEpochDay(),
            )
        )
        runCatching { repo.fillPhoto(name) }
    }

    fun update(
        p: Purchase, name: String, price: Double, qty: Int, date: LocalDate, mfg: LocalDate?, exp: LocalDate?,
        category: com.parag.pricescanner.scan.Category?,
    ) = viewModelScope.launch {
        val renamed = name != p.productName && p.barcode == null
        repo.update(
            p.copy(
                productName = name, unitPrice = price, quantity = qty,
                imageUrl = if (renamed) null else p.imageUrl,
                category = category?.name,
                mfgDate = mfg?.toEpochDay(), expiryDate = exp?.toEpochDay(),
                timestamp = if (Fmt.date(p.timestamp) == date) p.timestamp else timestampFor(date, p.timestamp),
                source = Purchase.SOURCE_MANUAL,
            )
        )
        if (renamed) runCatching { repo.fillPhoto(name) }
    }

    fun delete(p: Purchase) = viewModelScope.launch { repo.delete(p) }
    fun restore(p: Purchase) = viewModelScope.launch { repo.add(p) }

    suspend fun exportPdf(): File = withContext(Dispatchers.IO) {
        val s = state.value
        PdfExporter.export(app, s.filtered, s.filter.label())
    }
}
