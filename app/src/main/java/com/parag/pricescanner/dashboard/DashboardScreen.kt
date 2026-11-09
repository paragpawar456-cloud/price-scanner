package com.parag.pricescanner.dashboard

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parag.pricescanner.LocalNavPadding
import com.parag.pricescanner.data.Purchase
import com.parag.pricescanner.ui.components.AppCard
import com.parag.pricescanner.ui.components.CountUpText
import com.parag.pricescanner.ui.components.Donut
import com.parag.pricescanner.ui.components.EmptyState
import com.parag.pricescanner.ui.components.InkCard
import com.parag.pricescanner.ui.components.ProductThumb
import com.parag.pricescanner.ui.components.Ring
import com.parag.pricescanner.ui.components.SectionTitle
import com.parag.pricescanner.ui.components.Slice
import com.parag.pricescanner.ui.components.color
import com.parag.pricescanner.ui.components.enterUp
import com.parag.pricescanner.ui.theme.AppTheme
import com.parag.pricescanner.ui.theme.MoneyStyle
import com.parag.pricescanner.util.Fmt
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val dynamic by vm.dynamicColor.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = LocalNavPadding.current + 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(onSettings = { settings = true }, modifier = Modifier.enterUp(0)) }
        item { HeroCard(s, onSetBudget = { settings = true }, modifier = Modifier.enterUp(1)) }
        if (!s.loading && !s.hasData) {
            item {
                AppCard(Modifier.enterUp(2)) {
                    EmptyState(
                        Icons.Rounded.Insights,
                        "No purchases yet",
                        "Tap the Scan button, point at a pack's price and save the bill. Your charts appear here.",
                    )
                }
            }
            return@LazyColumn
        }
        item { QuickTiles(s, Modifier.enterUp(2)) }
        if (s.byCategory.isNotEmpty()) item { WhereItWent(s, Modifier.enterUp(3)) }
        if (s.expiring.isNotEmpty()) item { ExpiringRow(s.expiring, Modifier.enterUp(4)) }
        item {
            AppCard(Modifier.fillMaxWidth().enterUp(5)) {
                Column {
                    SectionTitle("Spending") {
                        Text(Fmt.moneyShort(s.rangeTotal), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChartRange.entries.forEach { r ->
                            FilterChip(
                                selected = s.range == r,
                                onClick = { vm.setRange(r) },
                                label = { Text(r.label) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    BarChart(s.bars, Modifier.fillMaxWidth())
                }
            }
        }
        item { TopProductsCard(s, Modifier.enterUp(6)) }
        item { RecentCard(s.recent, Modifier.enterUp(7)) }
        item {
            Text(
                "All-time spend " + Fmt.money(s.allTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (settings) {
        SettingsSheet(
            budget = s.budget,
            dynamic = dynamic,
            onDismiss = { settings = false },
            onSave = { b, d -> vm.setBudget(b); vm.setDynamicColor(d); settings = false },
        )
    }
}

@Composable
private fun Header(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val hello = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                Fmt.dayHeader(LocalDate.now()).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(hello, style = MaterialTheme.typography.headlineMedium)
        }
        Surface(
            onClick = onSettings,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.size(46.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Tune, "Budget and look") }
        }
    }
}

@Composable
private fun HeroCard(s: DashboardState, onSetBudget: () -> Unit, modifier: Modifier = Modifier) {
    val b = AppTheme.brand
    InkCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Spent in " + Fmt.monthYear(LocalDate.now()), color = b.onInkMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                CountUpText(s.month, Fmt::money, MoneyStyle.copy(fontSize = 36.sp), color = b.onInk)
                Spacer(Modifier.height(8.dp))
                val change = s.monthChange
                val pill = when {
                    change != null -> (if (change >= 0) "▲ " else "▼ ") + "%.0f%% vs last month".format(abs(change))
                    else -> "First month tracked"
                }
                Text(
                    pill,
                    color = if (change != null && change > 0) Color(0xFFFDA4AF) else b.accent,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(b.inkRaised, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (s.budget > 0) {
                val over = s.budgetUsed > 1f
                Ring(
                    progress = s.budgetUsed,
                    color = if (over) Color(0xFFFB7185) else b.accent,
                    track = b.inkLine,
                    modifier = Modifier.clickable(onClick = onSetBudget),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(s.budgetUsed * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = b.onInk)
                        Text("of " + Fmt.compact(s.budget), style = MaterialTheme.typography.labelSmall, color = b.onInkMuted)
                    }
                }
            } else {
                Surface(onClick = onSetBudget, shape = RoundedCornerShape(16.dp), color = b.inkRaised, contentColor = b.accent) {
                    Text("Set\nbudget", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (s.budget > 0) {
            Spacer(Modifier.height(6.dp))
            val left = s.budget - s.month
            Text(
                if (left >= 0) "${Fmt.money(left)} left this month" else "${Fmt.money(-left)} over budget",
                color = b.onInkMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        Sparkbars(s.last7)
    }
}

@Composable
private fun Sparkbars(bars: List<Bar>) {
    val b = AppTheme.brand
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(800, 200, FastOutSlowInEasing)) }
    val max = bars.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { i, bar ->
            val last = i == bars.lastIndex
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val frac = (0.12 + 0.88 * bar.value / max).toFloat() * grow.value
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .fillMaxHeight(frac.coerceAtLeast(0.05f))
                        .background(if (last) b.accent else b.inkLine, RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.height(6.dp))
                Text(bar.label, style = MaterialTheme.typography.labelSmall, color = if (last) b.onInk else b.onInkMuted)
            }
        }
    }
}

@Composable
private fun QuickTiles(s: DashboardState, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val todaySub = when {
            s.yesterday <= 0 -> "today"
            s.today >= s.yesterday -> "▲ vs yesterday"
            else -> "▼ vs yesterday"
        }
        Tile("Today", Fmt.moneyShort(s.today), todaySub, Modifier.weight(1f))
        Tile("Per day", Fmt.moneyShort(s.avgPerDay), "this month", Modifier.weight(1f))
        Tile("Items", "${s.monthItems}", "this month", Modifier.weight(1f))
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    AppCard(modifier, padding = 14.dp) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    sub.startsWith("▲") -> AppTheme.brand.bad
                    sub.startsWith("▼") -> AppTheme.brand.good
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WhereItWent(s: DashboardState, modifier: Modifier = Modifier) {
    val total = s.byCategory.sumOf { it.spend }
    AppCard(modifier.fillMaxWidth()) {
        Column {
            SectionTitle("Where it went") {
                Text(s.range.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Donut(s.byCategory.map { Slice(it.spend, it.category.color) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Fmt.compact(total), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.byCategory.take(5).forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(c.category.color, RoundedCornerShape(3.dp)))
                            Spacer(Modifier.width(8.dp))
                            Text(c.category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(Fmt.moneyShort(c.spend), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiringRow(items: List<Purchase>, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionTitle("Expiring soon")
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { p ->
                val exp = Fmt.epochDay(p.expiryDate) ?: return@items
                AppCard(Modifier.width(196.dp), padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductThumb(p.imageUrl, p.productName, size = 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.productName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            com.parag.pricescanner.scan.ExpiryTag(exp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopProductsCard(s: DashboardState, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column {
            SectionTitle("Top products") {
                Text(s.range.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            if (s.topProducts.isEmpty()) {
                Text("No purchases in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            val max = s.topProducts.maxOfOrNull { it.spend }?.takeIf { it > 0 } ?: 1.0
            s.topProducts.forEach { p ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductThumb(p.imageUrl, p.name, size = 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.money(p.spend), style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (p.spend / max).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
                        )
                        Text("qty ${p.qty}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(recent: List<Purchase>, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column {
            SectionTitle("Recent purchases")
            Spacer(Modifier.height(4.dp))
            recent.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductThumb(p.imageUrl, p.productName, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.productName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Fmt.dayShort(Fmt.date(p.timestamp))} · ${Fmt.time(p.timestamp)} · ×${p.quantity}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Fmt.money(p.total), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(budget: Double, dynamic: Boolean, onDismiss: () -> Unit, onSave: (Double, Boolean) -> Unit) {
    var text by remember { mutableStateOf(if (budget > 0) Fmt.price(budget) else "") }
    var dyn by remember { mutableStateOf(dynamic) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Budget and look", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Monthly budget (₹)") },
                supportingText = { Text("The ring on the dashboard shows how much of it you've used. Leave empty to hide it.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Match my wallpaper", style = MaterialTheme.typography.titleSmall)
                        Text("Use your wallpaper colour as the accent instead of lime.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dyn, onCheckedChange = { dyn = it })
                }
            }
            Button(
                onClick = { onSave(text.toDoubleOrNull() ?: 0.0, dyn) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
        }
    }
}
