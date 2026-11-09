package com.parag.pricescanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.parag.pricescanner.dashboard.DashboardScreen
import com.parag.pricescanner.data.Repository
import com.parag.pricescanner.history.HistoryScreen
import com.parag.pricescanner.scan.ScannerScreen
import com.parag.pricescanner.ui.theme.AppTheme
import com.parag.pricescanner.ui.theme.PriceScanTheme
import com.parag.pricescanner.util.Prefs
import kotlinx.coroutines.launch

/** Space screens leave at the bottom for the floating nav bar (above the system bar inset). */
val LocalNavPadding = compositionLocalOf<Dp> { 0.dp }
val NavPadding = 100.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dynamic by Prefs.get(LocalContext.current).dynamicColor.collectAsStateWithLifecycle()
            PriceScanTheme(dynamic = dynamic) { App() }
        }
        lifecycleScope.launch { runCatching { Repository.get(this@MainActivity).backfillPhotos() } }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Outlined.Dashboard, Icons.Rounded.Dashboard),
    Scan("scan", "Scan", Icons.Rounded.DocumentScanner, Icons.Rounded.DocumentScanner),
    Records("records", "Records", Icons.Outlined.ReceiptLong, Icons.Rounded.ReceiptLong),
}

@Composable
private fun App() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Tab.Scan.route
    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalNavPadding provides NavPadding) {
            NavHost(
                nav,
                startDestination = Tab.Scan.route,
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
            ) {
                composable(Tab.Scan.route) { ScannerScreen() }
                composable(Tab.Dashboard.route) { DashboardScreen() }
                composable(Tab.Records.route) { HistoryScreen() }
            }
        }
        FloatingNav(current, ::go, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FloatingNav(current: String, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val b = AppTheme.brand
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
            .height(76.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(b.ink, RoundedCornerShape(32.dp))
                .border(1.dp, b.inkLine, RoundedCornerShape(32.dp))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (tab != Tab.Scan) SideTab(tab, current == tab.route) { onTab(tab) }
                }
            }
        }
        val scanSelected = current == Tab.Scan.route
        val scale by animateFloatAsState(if (scanSelected) 1.06f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "scan")
        Surface(
            onClick = { onTab(Tab.Scan) },
            shape = CircleShape,
            color = b.accent,
            contentColor = b.onAccent,
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-10).dp)
                .size(66.dp)
                .scale(scale)
                .border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Tab.Scan.icon, "Scan", Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun SideTab(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val b = AppTheme.brand
    val bg by animateColorAsState(if (selected) b.inkLine else b.ink, label = "tabBg")
    val fg by animateColorAsState(if (selected) b.accent else b.onInkMuted, label = "tabFg")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = bg,
        contentColor = fg,
        modifier = Modifier.size(width = 60.dp, height = 44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, Modifier.size(24.dp))
        }
    }
}
