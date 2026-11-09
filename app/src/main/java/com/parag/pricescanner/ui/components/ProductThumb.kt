package com.parag.pricescanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Product photo from the web, or the product's first letter while loading / when there is none. */
@Composable
fun ProductThumb(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val shape = RoundedCornerShape(size / 4)
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier.size(size).clip(shape).background(if (loaded) Color.White else background),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.size(size),
            )
        }
    }
}
