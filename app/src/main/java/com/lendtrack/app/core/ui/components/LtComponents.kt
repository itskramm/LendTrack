package com.lendtrack.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.domain.model.InstallmentStatus
import com.lendtrack.app.domain.model.SyncStatus

// ─────────────────────────────────────────────────────────────────────────────
// LtStatCard — mirrors .stat in the prototype
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtStatCard(
    label: String,
    value: String,
    subtitle: String = "",
    valueColor: Color = LtBurgundy,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 3.dp,
                shape = CardShape,
                spotColor = LtShadow,
                ambientColor = LtShadow,
            ),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = LtSurface),
        border = BorderStroke(1.dp, LtCardBorder),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = LtMuted,
                        letterSpacing = 0.5.sp,
                    )
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = LtDusty,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LtMuted,
                    )
                }
            }
            // Decorative heart — bottom-right, mirrors .hh
            Text(
                text = "♥",
                color = LtDusty,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LtPanel — mirrors .panel with ribbon header and dashed inner border
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtPanel(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = PanelShape,
                spotColor = Color(0x17783746),
                ambientColor = Color(0x08783746),
            ),
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = LtSurface),
        border = BorderStroke(1.dp, LtCardBorder),
    ) {
        Box {
            // Dashed inner border overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp)
                    .border(
                        width = 1.dp,
                        color = LtBlush,
                        shape = RoundedCornerShape(13.dp),
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    // Header row: ribbon title + optional action button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LtRibbon(title = title)
                        action?.invoke()
                    }
                    content()
                }
            }
            
            // Corner ornaments — SVG-style vine/leaf decorations matching prototype
            // Top-left corner
            Canvas(
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.TopStart)
                    .offset(x = 3.dp, y = 3.dp)
            ) {
                drawCornerOrnament(LtDusty)
            }
            
            // Top-right corner (flipped horizontally)
            Canvas(
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = 3.dp)
            ) {
                drawCornerOrnament(LtDusty, flipped = true)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LtRibbon — the banner-shaped panel title from the prototype
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtRibbon(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RibbonShape)
            .background(LtRose)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = LtBackground,
        )
    }
}

// Polygon ribbon shape matching clip-path in HTML: polygon(0 0,100% 0,95% 50%,100% 100%,0 100%,5% 50%)
private val RibbonShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0f, 0f)
    lineTo(w, 0f)
    lineTo(w * 0.95f, h * 0.5f)
    lineTo(w, h)
    lineTo(0f, h)
    lineTo(w * 0.05f, h * 0.5f)
    close()
}

// ─────────────────────────────────────────────────────────────────────────────
// LtButton — primary (rose) and ghost (card bg) variants
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    ghost: Boolean = false,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val bgColor    = if (ghost) LtSurface else LtRose
    val textColor  = if (ghost) LtBurgundy else Color.White
    val borderColor = if (ghost) LtDusty else LtBurgundy

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor   = textColor,
            disabledContainerColor = bgColor.copy(alpha = 0.5f),
            disabledContentColor   = textColor.copy(alpha = 0.5f),
        ),
        border = BorderStroke(1.dp, borderColor),
        contentPadding = if (small) PaddingValues(horizontal = 10.dp, vertical = 3.dp)
                         else PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (small) 14.dp else 16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = if (small) MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
                    else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LtStatusBadge — Paid / Due Soon / Overdue / Upcoming / Pending
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtStatusBadge(status: InstallmentStatus, modifier: Modifier = Modifier) {
    val (bg, label) = when (status) {
        InstallmentStatus.PAID        -> StatusPaid     to "Paid"
        InstallmentStatus.DUE_SOON    -> StatusDueSoon  to "Due Soon"
        InstallmentStatus.OVERDUE     -> StatusOverdue  to "Overdue"
        InstallmentStatus.UPCOMING    -> StatusUpcoming to "Upcoming"
        InstallmentStatus.PARTIAL     -> LtAmber        to "Partial"
    }
    Box(
        modifier = modifier
            .clip(BadgeShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LtSyncBadge — displays sync status inline
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtSyncBadge(status: SyncStatus, modifier: Modifier = Modifier) {
    val (bg, label) = when (status) {
        SyncStatus.SYNCED          -> LtSage          to "Synced"
        SyncStatus.PENDING         -> LtAmber         to "Pending"
        SyncStatus.SYNCING         -> LtDusty         to "Syncing…"
        SyncStatus.FAILED          -> LtRed           to "Sync Failed"
        SyncStatus.REQUIRES_ATTENTION -> LtRed        to "Needs Review"
    }
    Box(
        modifier = modifier
            .clip(BadgeShape)
            .background(bg.copy(alpha = 0.15f))
            .border(1.dp, bg, BadgeShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = bg)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LtChip — filter chips for status filters
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg     = if (selected) LtRose else Color.Transparent
    val border = if (selected) LtRose else LtDusty
    val text   = if (selected) Color.White else LtBurgundy

    Box(
        modifier = modifier
            .clip(ChipShape)
            .background(bg)
            .border(1.dp, border, ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = text)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LtDivider — dotted-style horizontal rule matching `border-bottom:1px dotted`
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = LtDivider,
        thickness = 1.dp,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LtSectionHeader — section headings within panels
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = LtBurgundy,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LtNote — muted helper text (mirrors .note in prototype)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LtMuted,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LtToast — temporary snackbar-style notification (mirrors .toast)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtToast(message: String, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(animationSpec  = tween(200)),
        exit    = fadeOut(animationSpec = tween(400)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(LtBurgundy)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = message, color = LtBackground, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LtSearchField — styled search input
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search…",
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = InputShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = LtRose,
            unfocusedBorderColor = LtDusty,
            focusedContainerColor   = LtBackground,
            unfocusedContainerColor = LtBackground,
            cursorColor = LtRose,
        ),
        leadingIcon = if (leadingIcon != null) ({
            Icon(leadingIcon, contentDescription = null, tint = LtDusty, modifier = Modifier.size(18.dp))
        }) else null,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LtEmptyState — shown when a list has no items
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("♥", fontSize = 32.sp, color = LtBlush)
            Spacer(Modifier.height(8.dp))
            LtNote(message)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BorderStroke convenience — reusable import
// ─────────────────────────────────────────────────────────────────────────────

fun BorderStroke(width: Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)

// ─────────────────────────────────────────────────────────────────────────────
// Corner ornament drawing — matches the SVG vine/leaf decorations in prototype
// ─────────────────────────────────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerOrnament(
    color: Color,
    flipped: Boolean = false,
) {
    val strokeWidth = 1.5f
    val path = androidx.compose.ui.graphics.Path().apply {
        // Curved vine from top-left corner (or top-right if flipped)
        if (flipped) {
            moveTo(size.width, 0f)
            cubicTo(
                size.width, size.height * 0.2f,
                size.width * 0.8f, size.height * 0.3f,
                size.width * 0.6f, size.height * 0.35f,
            )
            // Small leaf accent
            moveTo(size.width * 0.7f, size.height * 0.25f)
            cubicTo(
                size.width * 0.65f, size.height * 0.28f,
                size.width * 0.68f, size.height * 0.32f,
                size.width * 0.72f, size.height * 0.3f,
            )
        } else {
            moveTo(0f, 0f)
            cubicTo(
                0f, size.height * 0.2f,
                size.width * 0.2f, size.height * 0.3f,
                size.width * 0.4f, size.height * 0.35f,
            )
            // Small leaf accent
            moveTo(size.width * 0.3f, size.height * 0.25f)
            cubicTo(
                size.width * 0.35f, size.height * 0.28f,
                size.width * 0.32f, size.height * 0.32f,
                size.width * 0.28f, size.height * 0.3f,
            )
        }
    }
    
    drawPath(
        path = path,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
    )
}
