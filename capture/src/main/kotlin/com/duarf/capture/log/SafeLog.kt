// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.capture.log

/**
 * Audited logging wrapper conforming to Section 14 invariant:
 * Accepts only enumerated event codes and numeric values. NEVER message content or personal data.
 */
object SafeLog {
    enum class EventCode(val code: Int) {
        LISTENER_CONNECTED(100),
        LISTENER_DISCONNECTED(101),
        NOTIFICATION_RECEIVED(102),
        NOTIFICATION_SKIPPED(103),
        NOTIFICATION_PARSED(104),
        QUEUE_DROPPED_OLDEST(105),
        ERROR_DEGRADED(199)
    }

    fun event(event: EventCode, count: Long = 0) {
        // Safe numeric logging only
    }
}
