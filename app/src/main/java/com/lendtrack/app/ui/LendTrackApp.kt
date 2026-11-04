package com.lendtrack.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lendtrack.app.core.database.entities.ActivityLogEntity
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.domain.model.ActivityType
import com.lendtrack.app.navigation.LendTrackNavGraph
import com.lendtrack.app.navigation.Screen
import com.lendtrack.app.navigation.primaryNavItems
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// AppViewModel — provides connectivity and sync state to the app shell
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LendTrackApp(
    onSignOut:    () -> Unit = {},
    appViewModel: AppShellViewModel = hiltViewModel(),
) {
    val navController  = rememberNavController()
    val navBackStack   by navController.currentBackStackEntryAsState()
    val currentRoute   = navBackStack?.destination?.route ?: Screen.Dashboard.route
    val isOnline       by appViewModel.isOnline.collectAsStateWithLifecycle()
    val pendingCount   by appViewModel.pendingCount.collectAsStateWithLifecycle()
    val attentionCount by appViewModel.requiresAttentionCount.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val isTablet      = configuration.screenWidthDp >= 600
    
    // Sidebar is always toggleable, but starts open in landscape
    var sidebarOpen by remember(isTablet) { 
        mutableStateOf(isTablet) 
    }

    fun navigate(route: String) {
        sidebarOpen = false
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }

    // Dot texture — matches prototype's radial-gradient background pattern
    // rgba(217,140,150,.13) 1px dots on 14px × 14px grid
    val dotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.13f)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                val spacing = 14.dp.toPx()
                val radius = 1.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) {
                        drawCircle(
                            color  = dotColor,
                            radius = radius,
                            center = Offset(x, y),
                        )
                        x += spacing
                    }
                    y += spacing
                }
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Main content (always takes available space) ────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                LtTopBar(
                    showMenuButton = true, // Always show menu button
                    onMenuClick    = { sidebarOpen = !sidebarOpen },
                )

                // Offline banner — slides in below the top bar
                AnimatedVisibility(
                    visible = !isOnline,
                    enter   = slideInVertically(tween(300)) { -it },
                    exit    = slideOutVertically(tween(300)) { -it },
                ) {
                    LtOfflineBanner()
                }

                // Requires-attention banner
                AnimatedVisibility(
                    visible = attentionCount > 0,
                    enter   = slideInVertically(tween(300)) { -it },
                    exit    = slideOutVertically(tween(300)) { -it },
                ) {
                    LtRequiresAttentionBanner(
                        count       = attentionCount,
                        onGoToSettings = { navigate(Screen.Settings.route) },
                    )
                }

                LendTrackNavGraph(navController = navController, onSignOut = onSignOut)
            }
        }

        // ── Sidebar drawer (toggleable on all screen sizes) ───────────────
        if (sidebarOpen) {
            // Scrim overlay — click to close
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LtScrim)
                    .clickable { sidebarOpen = false },
            )
        }
        
        AnimatedVisibility(
            visible = sidebarOpen,
            enter   = slideInHorizontally(tween(200)) { -it },
            exit    = slideOutHorizontally(tween(200)) { -it },
        ) {
            LtSidebar(
                currentRoute = currentRoute,
                onNavigate   = ::navigate,
                onClose      = { sidebarOpen = false },
                modifier     = Modifier.width(240.dp).fillMaxHeight(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Offline banner — shown whenever connectivity is lost
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtOfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.WifiOff,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "You're offline. Changes are saved locally and will sync when you reconnect.",
            style  = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.surface),
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Requires-attention banner — shown when the server rejected one or more ops
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtRequiresAttentionBanner(count: Int, onGoToSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LtRed.copy(alpha = 0.12f))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint     = LtRed,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "$count operation${if (count != 1) "s" else ""} need${if (count == 1) "s" else ""} your review — the server returned an error.",
            style    = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onGoToSettings) {
            Text("Review", color = LtRed, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sidebar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtSidebar(
    currentRoute: String,
    onNavigate:   (String) -> Unit,
    onClose:      () -> Unit,
    modifier:     Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp)),
    ) {
        // Dashed inner decoration
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Close button at the top right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close sidebar",
                        tint = LtBurgundy,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            
            LtLogo()
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                primaryNavItems.forEach { screen ->
                    LtNavItem(
                        screen   = screen,
                        selected = currentRoute == screen.route,
                        onClick  = { onNavigate(screen.route) },
                    )
                }
            }
            Text(
                text  = "\"Small steps make big\ndreams come true.\" ❀",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = LtBurgundy,
            )
        }
    }
}

@Composable
fun LtLogo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("♥", fontSize = 22.sp, color = LtRose)
        }
        Spacer(Modifier.height(4.dp))
        Text("LendTrack", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            "Your Lending, Organized.",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = GreatVibes,
                fontSize   = 22.sp,
                color      = LtRose,
            ),
        )
    }
}

@Composable
fun LtNavItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NavItemShape)
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .border(1.dp, if (selected) LtRose else Color.Transparent, NavItemShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            screen.icon,
            contentDescription = null,
            tint     = if (selected) LtRose else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            screen.label,
            style    = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (selected) LtBurgundy else MaterialTheme.colorScheme.onSurface,
                fontSize   = 14.sp,
            ),
            modifier = Modifier.weight(1f),
        )
        if (selected) Text("♥", fontSize = 11.sp, color = LtRose)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtTopBar(
    showMenuButton: Boolean,
    onMenuClick:    () -> Unit,
    appViewModel:   AppShellViewModel = hiltViewModel(),
) {
    val today          = LocalDate.now()
    val hour           = LocalTime.now().hour
    val greeting       = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val dateStr        = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
    val activities     by appViewModel.recentActivities.collectAsStateWithLifecycle()
    val unreadCount    by appViewModel.unreadCount.collectAsStateWithLifecycle()

    // Local toggle for the notification panel
    var notifOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showMenuButton) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = LtBurgundy)
            }
        }
        
        // Zelliyah branding on the left with ribbon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Zelliyah",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = GreatVibes,
                        fontSize   = 32.sp,
                    ),
                    color = LtRose,
                )
                Text(
                    "Lending Management",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            // Decorative ribbon
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LtRose.copy(alpha = 0.15f))
                    .border(1.dp, LtRose.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "♥",
                    fontSize = 18.sp,
                    color = LtRose,
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Greeting and date on the right
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "$greeting, Zelliyah!",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── G-1: Notification bell with unread badge and panel ────────────
        Box {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge(
                            containerColor = LtRose,
                            contentColor   = Color.White,
                        ) {
                            Text(
                                if (unreadCount > 9) "9+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
            ) {
                IconButton(
                    onClick  = { notifOpen = !notifOpen },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "Notifications${if (unreadCount > 0) ", $unreadCount unread" else ""}",
                        tint = LtBurgundy,
                    )
                }
            }

            // Notification panel dropdown
            DropdownMenu(
                expanded         = notifOpen,
                onDismissRequest = { notifOpen = false },
                offset           = DpOffset(x = (-160).dp, y = 4.dp),
                containerColor   = MaterialTheme.colorScheme.surface,
                modifier         = Modifier.width(340.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        "Notifications",
                        style = MaterialTheme.typography.headlineMedium,
                        color = LtBurgundy,
                    )
                    if (unreadCount > 0) {
                        TextButton(onClick = { appViewModel.markAllRead(); notifOpen = false }) {
                            Text("Mark all read", color = LtRose, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider(color = LtDivider)

                if (activities.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No recent activity.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    activities.take(8).forEach { act ->
                        NotificationItem(act)
                        HorizontalDivider(color = LtDivider)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Recent activity · updates in real time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Profile avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text("♡", fontSize = 18.sp, color = LtBurgundy)
        }
    }
    HorizontalDivider(color = LtDivider)
}

@Composable
private fun NotificationItem(act: ActivityLogEntity) {
    val (icon, color) = when (act.type) {
        ActivityType.PAYMENT      -> "₱" to LtRose
        ActivityType.WARNING      -> "⚠" to LtRed
        ActivityType.NEW_LOAN     -> "✚" to LtSage
        ActivityType.NEW_BORROWER -> "♡" to LtRose
        ActivityType.ADJUSTMENT   -> "✎" to LtAmber
        ActivityType.SYSTEM       -> "⚙" to LtMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Text(icon, color = color, fontSize = 14.sp, modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                act.description,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Text(
                act.occurredAt.toLocalDate().let {
                    it.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
