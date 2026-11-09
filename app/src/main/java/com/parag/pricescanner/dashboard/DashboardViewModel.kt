package com.parag.pricescanner.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parag.pricescanner.data.Purchase
import com.parag.pricescanner.data.Repository
import com.parag.pricescanner.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class ChartRange(val label: String) { WEEK("7 days"), MONTH("30 days"), YEAR("12 months") }

data class Bar(val label: String, val value: Double, val detail: String)
data class CategoryStat(val category: com.parag.pricescanner.scan.Category, val spend: Double, val qty: Int)
data class ProductStat(val name: String, val qty: Int, val spend: Double, val imageUrl: String? = null)

data class DashboardState(
    val loading: Boolean = true,
    val hasData: Boolean = false,
    val today: Double = 0.0,
    val yesterday: Double = 0.0,
    val week: Double = 0.0,
    val month: Double = 0.0,
    val lastMonth: Double = 0.0,
    val allTime: Double = 0.0,
    val monthItems: Int = 0,
    val avgPerDay: Double = 0.0,
    val range: ChartRange = ChartRange.WEEK,
    val bars: List<Bar> = emptyList(),
    val rangeTotal: Double = 0.0,
    val topProducts: List<ProductStat> = emptyList(),
    val recent: List<Purchase> = emptyList(),
    /** Purchases with an expiry date within the next 30 days or already past (last 60 days), soonest first. */
    val expiring: List<Purchase> = emptyList(),
    val byCategory: List<CategoryStat> = emptyList(),
) {
    /** Month-over-month change in percent, null when there is nothing to compare. */
    val monthChange: Double? get() = if (lastMonth > 0) (month - lastMonth) / lastMonth * 100 else null
}

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    private val range = MutableStateFlow(ChartRange.WEEK)

    val state: StateFlow<DashboardState> = combine(repo.purchases, range) { all, r -> compute(all, r) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun setRange(r: ChartRange) { range.value = r }

    private fun compute(all: List<Purchase>, r: ChartRange): DashboardState {
        val today = LocalDate.now()
        val byDay: Map<LocalDate, List<Purchase>> = all.groupBy { Fmt.date(it.timestamp) }
        fun sumDays(from: LocalDate, toInclusive: LocalDate): Double =
            byDay.filterKeys { !it.isBefore(from) && !it.isAfter(toInclusive) }.values.sumOf { l -> l.sumOf { it.total } }

        val monthStart = today.withDayOfMonth(1)
        val lastMonthStart = monthStart.minusMonths(1)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthPurchases = all.filter { !Fmt.date(it.timestamp).isBefore(monthStart) }
        val month = monthPurchases.sumOf { it.total }

        val (bars, rangeStart) = when (r) {
            ChartRange.WEEK -> (6 downTo 0).map { i ->
                val d = today.minusDays(i.toLong())
                val v = byDay[d].orEmpty().sumOf { it.total }
                Bar(Fmt.weekday(d).take(3), v, Fmt.dayShort(d))
            } to today.minusDays(6)
            ChartRange.MONTH -> (29 downTo 0).map { i ->
                val d = today.minusDays(i.toLong())
                val v = byDay[d].orEmpty().sumOf { it.total }
                Bar(if (i % 5 == 0) d.dayOfMonth.toString() else "", v, Fmt.dayShort(d))
            } to today.minusDays(29)
            ChartRange.YEAR -> (11 downTo 0).map { i ->
                val m = monthStart.minusMonths(i.toLong())
                val end = m.plusMonths(1).minusDays(1)
                Bar(Fmt.month(m), sumDays(m, end), Fmt.monthYear(m))
            } to monthStart.minusMonths(11)
        }

        val inRange = all.filter { !Fmt.date(it.timestamp).isBefore(rangeStart) }
        val top = inRange.groupBy { it.productName.trim().lowercase() }
            .map { (_, list) -> ProductStat(list.first().productName, list.sumOf { it.quantity }, list.sumOf { it.total }, list.firstNotNullOfOrNull { it.imageUrl }) }
            .sortedByDescending { it.spend }
            .take(5)

        return DashboardState(
            loading = false,
            hasData = all.isNotEmpty(),
            today = byDay[today].orEmpty().sumOf { it.total },
            yesterday = byDay[today.minusDays(1)].orEmpty().sumOf { it.total },
            week = sumDays(weekStart, today),
            month = month,
            lastMonth = sumDays(lastMonthStart, monthStart.minusDays(1)),
            allTime = all.sumOf { it.total },
            monthItems = monthPurchases.sumOf { it.quantity },
            avgPerDay = month / today.dayOfMonth,
            range = r,
            bars = bars,
            rangeTotal = bars.sumOf { it.value },
            topProducts = top,
            recent = all.take(6),
            byCategory = inRange.groupBy { com.parag.pricescanner.scan.Category.of(it.category) ?: com.parag.pricescanner.scan.Category.OTHER }
                .map { (c, l) -> CategoryStat(c, l.sumOf { it.total }, l.sumOf { it.quantity }) }
                .sortedByDescending { it.spend },
            expiring = all.filter { p ->
                p.expiryDate?.let { it <= today.plusDays(30).toEpochDay() && it >= today.minusDays(60).toEpochDay() } == true
            }.sortedBy { it.expiryDate }.take(6),
        )
    }
}
