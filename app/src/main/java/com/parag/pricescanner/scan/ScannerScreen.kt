@file:OptIn(ExperimentalMaterial3Api::class)

package com.parag.pricescanner.scan

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parag.pricescanner.ui.components.ItemDraft
import com.parag.pricescanner.ui.components.ItemEditorSheet
import com.parag.pricescanner.ui.components.QuantityStepper
import com.parag.pricescanner.ui.theme.Mint
import com.parag.pricescanner.util.Fmt
import kotlinx.coroutines.delay

@Composable
fun ScannerScreen(vm: ScannerViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    val state by vm.state.collectAsState()
    val names by vm.productNames.collectAsState(initial = emptyList())
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    var editing by remember { mutableStateOf<CartItem?>(null) }
    var capture by remember { mutableStateOf<Detection?>(null) }
    var captureOpen by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf<CountEvent?>(null) }

    // Pause analysis while an editor is open so the camera doesn't keep counting.
    LaunchedEffect(editing, captureOpen) { vm.setPaused(editing != null || captureOpen) }

    LaunchedEffect(state.lastCount?.id) {
        val ev = state.lastCount ?: return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        flash = ev
        delay(1300)
        if (flash?.id == ev.id) flash = null
    }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { snackbar.showSnackbar(it); vm.consumeSaved() }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (hasPermission) {
                val analyzer = remember { ScanAnalyzer(isPaused = vm::isPaused, onResult = vm::onFrame) }
                DisposableEffect(analyzer) { onDispose { analyzer.close() } }
                CameraPreview(analyzer = analyzer, torchOn = state.torchOn, modifier = Modifier.fillMaxSize())
                ScanOverlay(active = state.detection != null, modifier = Modifier.fillMaxSize())
                DetectionBubble(
                    state.detection,
                    Modifier.align(Alignment.Center).offset(y = (-120).dp),
                    onClick = { capture = state.detection; captureOpen = true },
                )
            } else {
                PermissionPrompt { launcher.launch(Manifest.permission.CAMERA) }
            }

            TopControls(
                autoMode = state.autoMode,
                torchOn = state.torchOn,
                onTorch = vm::toggleTorch,
                onMode = vm::setAutoMode,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = flash != null,
                enter = scaleIn(initialScale = 0.6f) + fadeIn(),
                exit = fadeOut() + scaleOut(targetScale = 1.1f),
                modifier = Modifier.align(Alignment.Center).offset(y = 70.dp),
            ) {
                val ev = flash
                if (ev != null) CountFlash(ev)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }

        BottomPanel(
            state = state,
            onCapture = { capture = state.detection; captureOpen = true },
            onCountCurrent = vm::countCurrent,
            onEdit = { editing = it },
            onQty = vm::changeQty,
            onClear = vm::clearCart,
            onSave = vm::saveCart,
        )
    }

    if (captureOpen) {
        val d = capture
        ItemEditorSheet(
            title = if (d == null) "Add item" else "Add scanned item",
            initial = ItemDraft(
                name = d?.displayName.orEmpty(), price = d?.price, qty = 1,
                mfgDate = d?.mfgDate, expiryDate = d?.expiryDate, category = d?.category,
            ),
            details = d?.details,
            priceSuggestions = d?.alternatives.orEmpty(),
            nutrition = d?.nutrition,
            imageUrl = d?.imageUrl,
            nameSuggestions = names,
            confirmLabel = "Add to list",
            onDismiss = { captureOpen = false },
            onSave = { draft ->
                vm.addManual(
                    draft.name, draft.price ?: 0.0, draft.qty, d?.barcode, draft.mfgDate, draft.expiryDate,
                    d?.nutrition, d?.imageUrl, draft.category, d?.details,
                )
                captureOpen = false
            },
        )
    }
    editing?.let { item ->
        ItemEditorSheet(
            title = "Edit item",
            initial = ItemDraft(item.name, item.price, item.qty, mfgDate = item.mfgDate, expiryDate = item.expiryDate, category = item.category),
            details = item.details,
            nutrition = item.nutrition,
            imageUrl = item.imageUrl,
            nameSuggestions = names,
            onDismiss = { editing = null },
            onSave = { draft ->
                vm.updateItem(
                    item.copy(
                        name = draft.name, price = draft.price ?: item.price, qty = draft.qty, auto = false,
                        mfgDate = draft.mfgDate, expiryDate = draft.expiryDate,
                        category = draft.category,
                        categoryLocked = item.categoryLocked || draft.category != item.category,
                    )
                )
                editing = null
            },
            onDelete = { vm.removeItem(item.id); editing = null },
        )
    }
}

@Composable
private fun TopControls(
    autoMode: Boolean,
    torchOn: Boolean,
    onTorch: () -> Unit,
    onMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val b = com.parag.pricescanner.ui.theme.AppTheme.brand
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onTorch,
            shape = CircleShape,
            color = if (torchOn) b.accent else b.ink.copy(alpha = 0.6f),
            contentColor = if (torchOn) b.onAccent else Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "Torch", Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        ModeSwitch(autoMode, onMode)
    }
}

@Composable
private fun ScanOverlay(active: Boolean, modifier: Modifier = Modifier) {
    val accent = com.parag.pricescanner.ui.theme.AppTheme.brand.accent
    val corner by animateColorAsState(if (active) accent else Color.White, label = "corner")
    val t = rememberInfiniteTransition(label = "scan")
    val line by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "line")
    Canvas(modifier) {
        val w = size.width * 0.8f
        val h = size.height * 0.34f
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2 - size.height * 0.04f
        val radius = 28.dp.toPx()
        val window = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, top + h, CornerRadius(radius)))
        }
        clipPath(window, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.45f)) }

        val len = 34.dp.toPx()
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        fun drawCorner(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply {
                moveTo(x, y + dy * len)
                lineTo(x, y + dy * radius * 0.4f)
                quadraticTo(x, y, x + dx * radius * 0.4f, y)
                lineTo(x + dx * len, y)
            }
            drawPath(p, corner, style = stroke)
        }
        drawCorner(left, top, 1f, 1f)
        drawCorner(left + w, top, -1f, 1f)
        drawCorner(left, top + h, 1f, -1f)
        drawCorner(left + w, top + h, -1f, -1f)

        val y = top + 12.dp.toPx() + (h - 24.dp.toPx()) * line
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, corner.copy(alpha = 0.9f), Color.Transparent), left, left + w),
            topLeft = Offset(left + 12.dp.toPx(), y),
            size = Size(w - 24.dp.toPx(), 2.5.dp.toPx()),
        )
    }
}

@Composable
private fun DetectionBubble(d: Detection?, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    AnimatedVisibility(visible = d != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (d == null) return@AnimatedVisibility
        val b = com.parag.pricescanner.ui.theme.AppTheme.brand
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            color = b.ink.copy(alpha = 0.86f),
            contentColor = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (d.imageUrl != null) {
                    com.parag.pricescanner.ui.components.ProductThumb(d.imageUrl, d.displayName.orEmpty(), size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                } else if (d.barcode != null) {
                    Icon(Icons.Rounded.QrCode2, null, tint = b.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    d.category?.let {
                        com.parag.pricescanner.ui.components.CategoryChip(it, onDark = true)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        d.price?.let { Fmt.money(it) } ?: "Price not found",
                        style = com.parag.pricescanner.ui.theme.MoneyStyle.copy(fontSize = 24.sp),
                        color = if (d.price != null) b.accent else Color.White,
                    )
                    d.displayName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                    }
                    if (d.mfgDate != null || d.expiryDate != null) {
                        Text(
                            listOfNotNull(
                                d.mfgDate?.let { "Mfg " + Fmt.dayFull(it) },
                                d.expiryDate?.let { "Exp " + Fmt.dayFull(it) },
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFDE68A),
                        )
                    }
                    d.nutrition?.let { n ->
                        val bits = listOfNotNull(
                            n.sugar?.let { "Sugar ${com.parag.pricescanner.scan.Nutrition.fmt(it)}g" },
                            n.saturatedFat?.let { "Sat fat ${com.parag.pricescanner.scan.Nutrition.fmt(it)}g" },
                            n.transFat?.let { "Trans ${com.parag.pricescanner.scan.Nutrition.fmt(it)}g" },
                        ).ifEmpty { listOfNotNull(n.energyKcal?.let { "${com.parag.pricescanner.scan.Nutrition.fmt(it)} kcal" }) }
                        if (bits.isNotEmpty()) {
                            Text(
                                bits.joinToString(" · ") + "  ›",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (n.warnings().isNotEmpty()) Color(0xFFFDA4AF) else Color(0xFFA7F3D0),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountFlash(ev: CountEvent) {
    val b = com.parag.pricescanner.ui.theme.AppTheme.brand
    Surface(shape = CircleShape, color = b.accent, contentColor = b.onAccent, shadowElevation = 8.dp) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null)
            Spacer(Modifier.width(8.dp))
            Text(
                "${ev.itemName}  ×${ev.qty}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.CameraAlt, null, tint = Color.White, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Camera access needed", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "PriceScan reads the MRP and barcode on a pack with your camera. Images never leave your phone.",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("Allow camera") }
    }
}

@Composable
private fun BottomPanel(
    state: ScannerUiState,
    onCapture: () -> Unit,
    onCountCurrent: () -> Unit,
    onEdit: (CartItem) -> Unit,
    onQty: (String, Int) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = com.parag.pricescanner.LocalNavPadding.current)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outline, CircleShape)
            )

            if (state.cart.isEmpty()) {
                Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.autoMode) Icons.Rounded.AutoMode else Icons.Rounded.TouchApp,
                        null, tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (state.autoMode) "Hold each packet in view until it beeps, then take it away before showing the next one. Price, barcode, dates and the nutrition table (turn the pack) are all added to the same item."
                        else "Point at the MRP or barcode and tap Capture, then set the quantity yourself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("This bill · ${state.cartQty} items", style = MaterialTheme.typography.titleSmall)
                        com.parag.pricescanner.ui.components.CountUpText(
                            state.cartTotal, Fmt::money, com.parag.pricescanner.ui.theme.MoneyStyle.copy(fontSize = 24.sp),
                        )
                    }
                    TextButton(onClick = onClear) {
                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
                LazyColumn(Modifier.heightIn(max = 210.dp)) {
                    items(state.cart, key = { it.id }) { item ->
                        CartRow(item, onClick = { onEdit(item) }, onQty = { onQty(item.id, it - item.qty) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.autoMode) {
                    FilledTonalButton(onClick = onCountCurrent, enabled = state.detection != null, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Rounded.AddCircleOutline, null)
                        Spacer(Modifier.width(6.dp))
                        Text("+1")
                    }
                } else {
                    FilledTonalButton(onClick = onCapture, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Rounded.DocumentScanner, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Capture")
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = state.cart.isNotEmpty() && state.cart.none { it.needsPrice },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(
                        if (state.cart.isEmpty()) "Save" else "Save  ·  ${Fmt.money(state.cartTotal)}",
                        maxLines = 1,
                    )
                }
            }
            if (state.autoMode) {
                TextButton(onClick = onCapture, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Add item by hand")
                }
            }
        }
    }
}

@Composable
private fun ModeSwitch(autoMode: Boolean, onMode: (Boolean) -> Unit) {
    val b = com.parag.pricescanner.ui.theme.AppTheme.brand
    Row(
        Modifier
            .background(b.ink.copy(alpha = 0.7f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(4.dp),
    ) {
        listOf(true to "Auto", false to "Manual").forEach { (auto, label) ->
            val selected = autoMode == auto
            val bg by animateColorAsState(if (selected) b.accent else Color.Transparent, label = "modeBg")
            val fg by animateColorAsState(if (selected) b.onAccent else Color.White, label = "modeFg")
            Surface(onClick = { onMode(auto) }, shape = CircleShape, color = bg, contentColor = fg) {
                Row(Modifier.height(38.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (auto) Icons.Rounded.AutoMode else Icons.Rounded.TouchApp, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun ExpiryTag(exp: java.time.LocalDate, modifier: Modifier = Modifier) {
    val days = Fmt.daysLeft(exp)
    val color = when {
        days < 0 -> MaterialTheme.colorScheme.error
        days <= 30 -> com.parag.pricescanner.ui.theme.AppTheme.brand.warn
        else -> com.parag.pricescanner.ui.theme.AppTheme.brand.good
    }
    Text(Fmt.expiryText(exp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
private fun CartRow(item: CartItem, onClick: () -> Unit, onQty: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.parag.pricescanner.ui.components.ProductThumb(item.imageUrl, item.name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.needsPrice) {
                Text("Tap to set price", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "${Fmt.money(item.price)} × ${item.qty}  =  ${Fmt.money(item.total)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item.category?.let { com.parag.pricescanner.ui.components.CategoryChip(it) }
                    item.expiryDate?.let { ExpiryTag(it) }
                    item.nutrition?.warnings()?.firstOrNull()?.let { com.parag.pricescanner.ui.components.WarningPill(it) }
                }
            }
        }
        QuantityStepper(qty = item.qty, onChange = onQty, compact = true)
    }
}
