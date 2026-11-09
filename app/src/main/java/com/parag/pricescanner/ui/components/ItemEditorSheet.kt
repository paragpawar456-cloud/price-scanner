package com.parag.pricescanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
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
)

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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial.name) }
    var priceText by remember { mutableStateOf(initial.price?.takeIf { it > 0 }?.let(Fmt::price).orEmpty()) }
    var qty by remember { mutableStateOf(initial.qty.coerceAtLeast(1)) }
    var qtyText by remember { mutableStateOf(initial.qty.coerceAtLeast(1).toString()) }
    var date by remember { mutableStateOf(initial.date ?: LocalDate.now()) }
    var showPicker by remember { mutableStateOf(false) }

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
            Text(title, style = MaterialTheme.typography.titleLarge)
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
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Date: " + Fmt.dayFull(date))
                }
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
                onClick = { onSave(ItemDraft(name.trim(), price, qty, if (showDate) date else null)) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(confirmLabel) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}
