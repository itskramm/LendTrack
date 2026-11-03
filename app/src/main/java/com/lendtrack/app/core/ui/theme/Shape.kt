package com.lendtrack.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Shape system — mirrors the prototype's rounded-corner language
// ─────────────────────────────────────────────────────────────────────────────

val LendTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // badges, chips
    small      = RoundedCornerShape(8.dp),   // inputs, small buttons
    medium     = RoundedCornerShape(12.dp),  // nav items, icon buttons
    large      = RoundedCornerShape(16.dp),  // stat cards
    extraLarge = RoundedCornerShape(18.dp),  // panels, modals
)

// Convenience shapes for specific components
val CardShape        = RoundedCornerShape(16.dp)
val PanelShape       = RoundedCornerShape(18.dp)
val ModalShape       = RoundedCornerShape(18.dp)
val ButtonShape      = RoundedCornerShape(10.dp)
val ChipShape        = RoundedCornerShape(18.dp)
val BadgeShape       = RoundedCornerShape(12.dp)
val InputShape       = RoundedCornerShape(8.dp)
val NavItemShape     = RoundedCornerShape(12.dp)
val CalendarDayShape = RoundedCornerShape(8.dp)
val SidebarShape     = RoundedCornerShape(0.dp)
