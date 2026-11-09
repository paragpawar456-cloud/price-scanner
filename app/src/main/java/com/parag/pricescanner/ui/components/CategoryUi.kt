package com.parag.pricescanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parag.pricescanner.scan.Category
import com.parag.pricescanner.scan.ProductDetails

val Category.icon: ImageVector
    get() = when (this) {
        Category.FOOD -> Icons.Rounded.Fastfood
        Category.GROCERY -> Icons.Rounded.LocalGroceryStore
        Category.MEDICINE -> Icons.Rounded.Medication
        Category.CLOTHING -> Icons.Rounded.Checkroom
        Category.PERSONAL_CARE -> Icons.Rounded.Spa
        Category.HOUSEHOLD -> Icons.Rounded.CleaningServices
        Category.ELECTRONICS -> Icons.Rounded.Devices
        Category.OTHER -> Icons.Rounded.Inventory2
    }

val Category.color: Color
    get() = when (this) {
        Category.FOOD -> Color(0xFFF97316)
        Category.GROCERY -> Color(0xFF16A34A)
        Category.MEDICINE -> Color(0xFFE11D48)
        Category.CLOTHING -> Color(0xFF7C3AED)
        Category.PERSONAL_CARE -> Color(0xFFDB2777)
        Category.HOUSEHOLD -> Color(0xFF0EA5E9)
        Category.ELECTRONICS -> Color(0xFF475569)
        Category.OTHER -> Color(0xFF64748B)
    }

@Composable
fun CategoryChip(category: Category, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val c = category.color
    Row(
        modifier
            .background(if (onDark) c else c.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon, null, tint = if (onDark) Color.White else c, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            category.label,
            color = if (onDark) Color.White else c,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun CategoryPicker(selected: Category?, onSelect: (Category) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Category.entries) { c ->
            FilterChip(
                selected = selected == c,
                onClick = { onSelect(c) },
                label = { Text(c.label) },
                leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp), tint = if (selected == c) c else MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.16f)),
            )
        }
    }
}

@Composable
fun DetailsCard(details: ProductDetails, category: Category?, modifier: Modifier = Modifier) {
    val rows = details.rows(category)
    if (rows.isEmpty()) return
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Product information", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            category?.let { CategoryChip(it) }
        }
        Spacer(Modifier.height(6.dp))
        rows.forEachIndexed { i, (label, value) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val alert = label == "Prescription" || label == "Warnings" || label == "Allergens"
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 96.dp, max = 110.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (alert) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

