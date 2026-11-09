@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.parag.pricescanner.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add entry") },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Records", style = MaterialTheme.typography.headlineMedium)
                        Text(s.filter.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = ::export, enabled = !exporting) {
                        if (exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.PictureAsPdf, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF")
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Period.entries) { p ->
                        FilterChip(
                            selected = s.filter.period == p,
                            onClick = { if (p == Period.CUSTOM) pickRange = true else vm.setPeriod(p) },
                            label = { Text(p.label) },
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
                        "Scan items on the Scan tab, or tap Add entry to record a purchase by hand.",
                        Modifier.padding(top = 24.dp),
                    )
                }
            }

            s.groups.forEach { g ->
                stickyHeader(key = "h_${g.date}") {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Fmt.dayHeader(g.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                "${g.qty} items · ${Fmt.money(g.total)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                items(g.items, key = { it.id }) { p ->
                    PurchaseRow(p, onClick = { editing = p })
                    HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
                vm.add(d.name, d.price ?: 0.0, d.qty, d.date ?: java.time.LocalDate.now(), d.mfgDate, d.expiryDate)
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
            ),
            nameSuggestions = names,
            showDate = true,
            onDismiss = { editing = null },
            onSave = { d ->
                vm.update(p, d.name, d.price ?: p.unitPrice, d.qty, d.date ?: Fmt.date(p.timestamp), d.mfgDate, d.expiryDate)
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
private fun SummaryStrip(s: HistoryState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Summary("Spent", Fmt.money(s.total), Modifier.weight(1.4f))
        Summary("Items", "${s.qty}", Modifier.weight(1f))
        Summary("Days", "${s.groups.size}", Modifier.weight(0.8f))
    }
}

@Composable
private fun Summary(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
    }
}

@Composable
private fun PurchaseRow(p: Purchase, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                p.productName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.productName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.source == Purchase.SOURCE_AUTO) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.AutoMode, "Auto counted", tint = Mint, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                "${p.quantity} × ${Fmt.money(p.unitPrice)} · ${Fmt.time(p.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        }
        Text(Fmt.money(p.total), style = MaterialTheme.typography.titleSmall)
    }
}
