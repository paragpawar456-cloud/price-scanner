@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.parag.pricescanner.history

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.parag.pricescanner.ui.components.color
import com.parag.pricescanner.ui.components.icon
import com.parag.pricescanner.ui.components.enterUp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parag.pricescanner.data.Purchase
import com.parag.pricescanner.export.PdfExporter
import com.parag.pricescanner.ui.components.EmptyState
import com.parag.pricescanner.ui.components.ItemDraft
import com.parag.pricescanner.ui.components.ItemEditorSheet
import com.parag.pricescanner.ui.theme.Mint
import com.parag.pricescanner.util.Fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val names by vm.productNames.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<Purchase?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pickRange by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<java.io.File?>(null) }

    fun export() {
        if (exporting) return
        exporting = true
        scope.launch {
            try {
                ready = vm.exportPdf()
            } catch (e: Exception) {
                snackbar.showSnackbar("Export failed: ${e.message}")
            } finally {
                exporting = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = com.parag.pricescanner.LocalNavPadding.current)) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).statusBarsPadding(),
            contentPadding = PaddingValues(bottom = com.parag.pricescanner.LocalNavPadding.current + 40.dp),
        ) {
            item {
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).enterUp(0),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Records", style = MaterialTheme.typography.headlineMedium)
                        Text(s.filter.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        onClick = { adding = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.size(44.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, "Add entry") } }
                    Surface(
                        onClick = ::export,
                        enabled = !exporting,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Row(Modifier.height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDF", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = s.filter.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search products") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (s.filter.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).enterUp(1),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Period.entries) { p ->
                        FilterChip(
                            selected = s.filter.period == p,
                            onClick = { if (p == Period.CUSTOM) pickRange = true else vm.setPeriod(p) },
                            label = { Text(p.label) },
                            shape = CircleShape,
                            colors = inkChip(),
                        )
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = s.filter.category == null, onClick = { vm.setCategory(null) }, label = { Text("All types") }, shape = CircleShape, colors = inkChip())
                    }
                    items(com.parag.pricescanner.scan.Category.entries) { c ->
                        FilterChip(
                            selected = s.filter.category == c,
                            onClick = { vm.setCategory(if (s.filter.category == c) null else c) },
                            label = { Text(c.label) },
                            shape = CircleShape,
                            leadingIcon = { Icon(c.icon, null, Modifier.size(16.dp), tint = if (s.filter.category == c) MaterialTheme.colorScheme.onPrimary else c.color) },
                            colors = inkChip(),
                        )
                    }
                }
            }
            item { SummaryStrip(s) }

            if (!s.loading && s.groups.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.EventNote,
                        "No records here",
                        "Scan items with the Scan button, or tap + to record a purchase by hand.",
                        Modifier.padding(top = 24.dp),
                    )
                }
            }

            s.groups.forEach { g ->
                stickyHeader(key = "h_${g.date}") {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                Fmt.dayHeader(g.date).uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(Fmt.money(g.total), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                itemsIndexed(g.items, key = { _, it -> it.id }) { i, p ->
                    val top = if (i == 0) 22.dp else 0.dp
                    val bottom = if (i == g.items.lastIndex) 22.dp else 0.dp
                    val shape = RoundedCornerShape(top, top, bottom, bottom)
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        PurchaseRow(p, onClick = { editing = p })
                        if (i != g.items.lastIndex) HorizontalDivider(Modifier.padding(start = 76.dp, end = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (adding) {
        ItemEditorSheet(
            title = "Add purchase",
            initial = ItemDraft(),
            nameSuggestions = names,
            showDate = true,
            onDismiss = { adding = false },
            onSave = { d ->
                vm.add(d.name, d.price ?: 0.0, d.qty, d.date ?: java.time.LocalDate.now(), d.mfgDate, d.expiryDate, d.category)
                adding = false
            },
        )
    }
    editing?.let { p ->
        ItemEditorSheet(
            title = "Edit purchase",
            initial = ItemDraft(
                p.productName, p.unitPrice, p.quantity, Fmt.date(p.timestamp),
                Fmt.epochDay(p.mfgDate), Fmt.epochDay(p.expiryDate),
                com.parag.pricescanner.scan.Category.of(p.category),
            ),
            details = com.parag.pricescanner.scan.ProductDetails.decode(p.details),
            nutrition = com.parag.pricescanner.scan.Nutrition.decode(p.nutrition),
            imageUrl = p.imageUrl,
            nameSuggestions = names,
            showDate = true,
            onDismiss = { editing = null },
            onSave = { d ->
                vm.update(p, d.name, d.price ?: p.unitPrice, d.qty, d.date ?: Fmt.date(p.timestamp), d.mfgDate, d.expiryDate, d.category)
                editing = null
            },
            onDelete = {
                vm.delete(p)
                editing = null
                scope.launch {
                    val r = snackbar.showSnackbar("Deleted ${p.productName}", actionLabel = "Undo")
                    if (r == SnackbarResult.ActionPerformed) vm.restore(p)
                }
            },
        )
    }
    ready?.let { file ->
        AlertDialog(
            onDismissRequest = { ready = null },
            icon = { Icon(Icons.Rounded.PictureAsPdf, null) },
            title = { Text("Report ready") },
            text = { Text("${s.filter.label()}\n${s.filtered.size} entries · ${Fmt.money(s.total)}") },
            confirmButton = {
                TextButton(onClick = { PdfExporter.share(context, file); ready = null }) { Text("Share / Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (!PdfExporter.open(context, file)) PdfExporter.share(context, file)
                    ready = null
                }) { Text("Open") }
            },
        )
    }
    if (pickRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis!!.toDate()
                        val end = state.selectedEndDateMillis?.toDate() ?: start
                        vm.setCustom(start, end)
                        pickRange = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(480.dp), title = {
                Text("Select dates", Modifier.padding(start = 24.dp, top = 16.dp))
            })
        }
    }
}

private fun Long.toDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun inkChip() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun SummaryStrip(s: HistoryState) {
    val b = com.parag.pricescanner.ui.theme.AppTheme.brand
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).enterUp(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1.5f).background(b.ink, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Spent", style = MaterialTheme.typography.labelMedium, color = b.onInkMuted)
            com.parag.pricescanner.ui.components.CountUpText(s.total, Fmt::money, MaterialTheme.typography.titleLarge, color = b.onInk)
        }
        Summary("Items", "${s.qty}", Modifier.weight(1f))
        Summary("Days", "${s.groups.size}", Modifier.weight(0.8f))
    }
}

@Composable
private fun Summary(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}

@Composable
private fun PurchaseRow(p: Purchase, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.parag.pricescanner.ui.components.ProductThumb(p.imageUrl, p.productName, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.productName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.source == Purchase.SOURCE_AUTO) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.AutoMode, "Auto counted", tint = com.parag.pricescanner.ui.theme.AppTheme.brand.good, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                "${p.quantity} × ${Fmt.money(p.unitPrice)} · ${Fmt.time(p.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            com.parag.pricescanner.scan.Category.of(p.category)?.let {
                com.parag.pricescanner.ui.components.CategoryChip(it, Modifier.padding(top = 3.dp))
            }
            val mfg = Fmt.epochDay(p.mfgDate)
            val exp = Fmt.epochDay(p.expiryDate)
            if (mfg != null || exp != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    mfg?.let {
                        Text("Mfg ${Fmt.dayFull(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (exp != null) Text("  ·  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    exp?.let { com.parag.pricescanner.scan.ExpiryTag(it) }
                }
            }
            com.parag.pricescanner.scan.Nutrition.decode(p.nutrition)?.warnings()?.takeIf { it.isNotEmpty() }?.let { w ->
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    w.take(3).forEach { com.parag.pricescanner.ui.components.WarningPill(it) }
                }
            }
        }
        Text(Fmt.money(p.total), style = MaterialTheme.typography.titleSmall)
    }
}
