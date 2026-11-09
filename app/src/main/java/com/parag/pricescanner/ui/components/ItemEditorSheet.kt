package com.parag.pricescanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parag.pricescanner.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class ItemDraft(
    val name: String = "",
    val price: Double? = null,
    val qty: Int = 1,
    val date: LocalDate? = null,
    val mfgDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val category: com.parag.pricescanner.scan.Category? = null,
)

private enum class PickFor { DATE, MFG, EXPIRY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorSheet(
    title: String,
    initial: ItemDraft,
    onDismiss: () -> Unit,
    onSave: (ItemDraft) -> Unit,
    confirmLabel: String = "Save",
    priceSuggestions: List<Double> = emptyList(),
    nameSuggestions: List<String> = emptyList(),
    showDate: Boolean = false,
    onDelete: (() -> Unit)? = null,
    nutrition: com.parag.pricescanner.scan.Nutrition? = null,
    imageUrl: String? = null,
    details: com.parag.pricescanner.scan.ProductDetails? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial.name) }
    var priceText by remember { mutableStateOf(initial.price?.takeIf { it > 0 }?.let(Fmt::price).orEmpty()) }
    var qty by remember { mutableStateOf(initial.qty.coerceAtLeast(1)) }
    var qtyText by remember { mutableStateOf(initial.qty.coerceAtLeast(1).toString()) }
    var date by remember { mutableStateOf(initial.date ?: LocalDate.now()) }
    var mfg by remember { mutableStateOf(initial.mfgDate) }
    var category by remember { mutableStateOf(initial.category) }
    var exp by remember { mutableStateOf(initial.expiryDate) }
    var pickFor by remember { mutableStateOf<PickFor?>(null) }

    val price = priceText.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && price != null && price > 0 && qty > 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (imageUrl != null) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ProductThumb(imageUrl, initial.name, size = 160.dp, background = MaterialTheme.colorScheme.surfaceContainer)
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                category?.let { CategoryChip(it) }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Product name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            val matches = remember(name, nameSuggestions) {
                nameSuggestions.filter { it.contains(name.trim(), ignoreCase = true) && !it.equals(name.trim(), true) }.take(8)
            }
            if (matches.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    items(matches) { s -> SuggestionChip(onClick = { name = s }, label = { Text(s) }) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            CategoryPicker(category, { category = it })
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = priceText,
                onValueChange = { v -> priceText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                label = { Text("Unit price") },
                prefix = { Text("₹ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (priceSuggestions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Text("Also seen:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
                    priceSuggestions.take(3).forEach { p ->
                        SuggestionChip(onClick = { priceText = Fmt.price(p) }, label = { Text("₹" + Fmt.price(p)) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { v ->
                        qtyText = v.filter(Char::isDigit).take(5)
                        qtyText.toIntOrNull()?.let { qty = it }
                    },
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                QuantityStepper(qty = qty, min = 1, onChange = { qty = it; qtyText = it.toString() })
            }

            if (showDate) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { pickFor = PickFor.DATE }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Purchase date: " + Fmt.dayFull(date))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Mfg / packed", mfg, { pickFor = PickFor.MFG }, { mfg = null }, Modifier.weight(1f))
                DateField("Expiry / use by", exp, { pickFor = PickFor.EXPIRY }, { exp = null }, Modifier.weight(1f))
            }

            if (details != null && !details.isEmpty) {
                Spacer(Modifier.height(12.dp))
                DetailsCard(details, category)
            }
            if (nutrition != null && !nutrition.isEmpty && (category == null || category!!.hasNutrition)) {
                Spacer(Modifier.height(12.dp))
                NutritionCard(nutrition)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (price != null && price > 0) "Total  " + Fmt.money(price * qty) else "Enter a price",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSave(ItemDraft(name.trim(), price, qty, if (showDate) date else null, mfg, exp, category)) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
            ) { Text(confirmLabel, style = MaterialTheme.typography.titleSmall) }
            Spacer(Modifier.height(16.dp))
        }
    }

    pickFor?.let { target ->
        val start = when (target) { PickFor.DATE -> date; PickFor.MFG -> mfg ?: LocalDate.now(); PickFor.EXPIRY -> exp ?: LocalDate.now() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickFor = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        when (target) { PickFor.DATE -> date = d; PickFor.MFG -> mfg = d; PickFor.EXPIRY -> exp = d }
                    }
                    pickFor = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickFor = null }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun DateField(label: String, value: LocalDate?, onPick: () -> Unit, onClear: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onPick, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value?.let(Fmt::dayFull) ?: "Not set", style = MaterialTheme.typography.bodyMedium)
        }
        if (value != null) {
            androidx.compose.material3.IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Close, "Clear", Modifier.size(16.dp))
            }
        }
    }
}
