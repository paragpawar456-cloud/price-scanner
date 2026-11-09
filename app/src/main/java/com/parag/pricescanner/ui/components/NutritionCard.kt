package com.parag.pricescanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parag.pricescanner.scan.Nutrition
import com.parag.pricescanner.ui.theme.Amber
import com.parag.pricescanner.ui.theme.Mint
import com.parag.pricescanner.ui.theme.Rose

fun levelColor(level: Nutrition.Level?): Color? = when (level) {
    Nutrition.Level.LOW -> Mint
    Nutrition.Level.MEDIUM -> Amber
    Nutrition.Level.HIGH -> Rose
    null -> null
}

@Composable
fun NutritionCard(n: Nutrition, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nutrition", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("per 100 g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val warnings = n.warnings()
        if (warnings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { warnings.take(3).forEach { WarningPill(it) } }
        }
        Spacer(Modifier.height(6.dp))
        n.rows().forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                val c = levelColor(row.level)
                Box(Modifier.size(8.dp).background(c ?: Color.Transparent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(row.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c ?: MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Green low · amber medium · red high (UK FSA / WHO guide). Values come from the pack or Open Food Facts.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WarningPill(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(Rose, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
