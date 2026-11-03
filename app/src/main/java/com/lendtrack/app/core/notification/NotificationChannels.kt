package com.lendtrack.app.core.notification

object NotificationChannels {
    const val DUE_DATES = "lendtrack_due_dates"
}

object NotificationIds {
    const val DUE_TODAY_GROUP  = 1000
    const val OVERDUE_GROUP    = 2000
    // Individual notifications use borrower-hash-based IDs above 3000
    const val INDIVIDUAL_BASE  = 3000
}
