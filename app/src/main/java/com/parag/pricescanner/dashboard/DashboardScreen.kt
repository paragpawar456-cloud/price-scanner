package com.parag.pricescanner.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parag.pricescanner.data.Purchase
import com.parag.pricescanner.ui.components.AppCard
import com.parag.pricescanner.ui.components.EmptyState
import com.parag.pricescanner.ui.components.IconBadge
import com.parag.pricescanner.ui.components.SectionTitle
import com.parag.pricescanner.ui.theme.Amber
import com.parag.pricescanner.ui.theme.Indigo
import com.parag.pricescanner.ui.theme.IndigoDeep
import com.parag.pricescanner.ui.theme.Mint
import com.parag.pricescanner.ui.theme.Rose
import com.parag.pricescanner.ui.theme.Sky
import com.parag.pricescanner.ui.theme.Violet
import com.parag.pricescanner.util.Fmt
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Greeting() }
        item { HeroCard(s) }
        if (!s.loading && !s.hasData) {
            item {
                AppCard {
                    EmptyState(
                        Icons.Rounded.Insights,
                        "No purchases yet",
                        "Scan a product on the Scan tab and save it. Your spending charts will appear here.",
                    )
                }
            }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Rounded.Today, Sky, "Today", Fmt.moneyShort(s.today),
                    compare(s.today, s.yesterday, "yesterday"), Modifier.weight(1f),
                )
                StatTile(
                    Icons.Rounded.DateRange, Violet, "This week", Fmt.moneyShort(s.week),
                    null, Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Rounded.CalendarToday, Amber, "Avg per day", Fmt.moneyShort(s.avgPerDay),
                    "this month", Modifier.weight(1f),
                )
                StatTile(
                    Icons.Rounded.ShoppingBag, Mint, "Items bought", "${s.monthItems}",
                    "this month", Modifier.weight(1f),
                )
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionTitle("Spending") {
                        Text(Fmt.moneyShort(s.rangeTotal), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChartRange.entries.forEach { r ->
                            FilterChip(selected = s.range == r, onClick = { vm.setRange(r) }, label = { Text(r.label) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    BarChart(s.bars, Modifier.fillMaxWidth())
                }
            }
        }
        if (s.expiring.isNotEmpty()) item { ExpiringCard(s.expiring) }
        item { TopProductsCard(s) }
        item { RecentCard(s.recent) }
        item {
            Text(
                "All-time spend " + Fmt.money(s.allTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}

private fun compare(now: Double, before: Double, label: String): String? {
    if (before <= 0) return null
    val pct = (now - before) / before * 100
    return (if (pct >= 0) "▲ " else "▼ ") + "%.0f%% vs %s".format(abs(pct), label)
}

@Composable
private fun Greeting() {
    val hour = LocalTime.now().hour
    val hello = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Text(Fmt.dayHeader(LocalDate.now()).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hello, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun HeroCard(s: DashboardState) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(IndigoDeep, Indigo, Violet)))
            .padding(22.dp),
    ) {
        Column {
            Text(
                "Spent in " + Fmt.monthYear(LocalDate.now()),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(Fmt.money(s.month), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(10.dp))
            val change = s.monthChange
            if (change != null) {
                val up = change >= 0
                Row(
                    Modifier.background(Color.White.copy(alpha = 0.16f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (up) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, tint = if (up) Color(0xFFFECACA) else Color(0xFFA7F3D0), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "%.0f%% %s last month (%s)".format(abs(change), if (up) "above" else "below", Fmt.moneyShort(s.lastMonth)),
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                Text("Track every purchase by scanning its price", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, tint: Color, label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column {
            IconBadge(icon, tint, size = 36.dp)
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                sub ?: " ",
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    sub?.startsWith("▲") == true -> Rose
                    sub?.startsWith("▼") == true -> Mint
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

private val rankColors = listOf(Indigo, Mint, Amber, Sky, Violet)

@Composable
private fun TopProductsCard(s: DashboardState) {
    AppCard(Modifier.fillMaxWidth()) {
        Column {
            SectionTitle("Top products") {
                Text(s.range.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            if (s.topProducts.isEmpty()) {
                Text("No purchases in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            val max = s.topProducts.maxOfOrNull { it.spend }?.takeIf { it > 0 } ?: 1.0
            s.topProducts.forEachIndexed { i, p ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(26.dp).background(rankColors[i % rankColors.size].copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", color = rankColors[i % rankColors.size], fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        Spacer(Modifier.width(10.dp))
                        Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.money(p.spend), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("qty ${p.qty}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (p.spend / max).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = rankColors[i % rankColors.size],
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpiringCard(items: List<Purchase>) {
    AppCard(Modifier.fillMaxWidth()) {
        Column {
            SectionTitle("Expiring soon") {
                Icon(Icons.Rounded.WarningAmber, null, tint = Amber)
            }
            Spacer(Modifier.height(6.dp))
            items.forEach { p ->
                val exp = Fmt.epochDay(p.expiryDate) ?: return@forEach
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.EventBusy, if (Fmt.daysLeft(exp) < 0) Rose else Amber, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.productName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        com.parag.pricescanner.scan.ExpiryTag(exp)
                    }
                    Text("qty ${p.quantity}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RecentCard(recent: List<Purchase>) {
    AppCard(Modifier.fillMaxWidth()) {
        Column {
            SectionTitle("Recent purchases")
            Spacer(Modifier.height(6.dp))
            recent.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.ShoppingBag, MaterialTheme.colorScheme.primary, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.productName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Fmt.dayShort(Fmt.date(p.timestamp))} · ${Fmt.time(p.timestamp)} · qty ${p.quantity}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Fmt.money(p.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
