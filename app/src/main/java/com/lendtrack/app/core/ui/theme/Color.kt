package com.lendtrack.app.core.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// LendTrack brand palette — matched exactly to the HTML prototype CSS variables
// ─────────────────────────────────────────────────────────────────────────────

/** Warm off-white page background  `--bg:#FFF5F0` */
val LtBackground    = Color(0xFFFFF5F0)

/** Slightly cooler card surface    `--card:#FFFCFA` */
val LtSurface       = Color(0xFFFFFCFA)

/** Soft blush for sidebar & accents `--blush:#F6D8D8` */
val LtBlush         = Color(0xFFF6D8D8)

/** Dusty rose — borders, secondary `--dusty:#D98C96` */
val LtDusty         = Color(0xFFD98C96)

/** Primary rose — buttons, active   `--rose:#C45C72` */
val LtRose          = Color(0xFFC45C72)

/** Deep burgundy — headings, primary `--burg:#783746` */
val LtBurgundy      = Color(0xFF783746)

/** Dark warm brown — body text      `--text:#4B3035` */
val LtText          = Color(0xFF4B3035)

/** Muted mauve — secondary text     `--muted:#96757A` */
val LtMuted         = Color(0xFF96757A)

/** Sage green — "Paid" badge        `--sage:#6F8F72` */
val LtSage          = Color(0xFF6F8F72)

/** Warm amber — "Due Soon" badge    `--amber:#C99A3C` */
val LtAmber         = Color(0xFFC99A3C)

/** Soft red — "Overdue" badge, errors `--red:#A8454F` */
val LtRed           = Color(0xFFA8454F)

// Status badge colors for direct use
val StatusPaid      = LtSage
val StatusDueSoon   = LtAmber
val StatusOverdue   = LtRed
val StatusUpcoming  = LtDusty
val StatusPending   = Color(0xFF8B7BA0)   // soft purple — pending sync

// Utility shades
val LtDivider       = Color(0x33D98C96)   // 20% dusty
val LtScrim         = Color(0x734B3035)   // 45% text — modal backdrop
val LtCardBorder    = LtDusty
val LtShadow        = Color(0x14783746)   // 8% burgundy — card shadow

// Inverse surface (used for toast/snackbar)
val LtOnInverse     = Color(0xFFFFF5F0)
