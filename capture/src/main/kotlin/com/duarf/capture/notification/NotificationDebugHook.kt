// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.capture.notification

import android.content.Context
import android.service.notification.StatusBarNotification

/**
 * Hook for debug monitoring and recording of notifications (§16.3).
 * Clean decoupling interface: main source set contains zero class-name strings or reflection.
 * Implementations are registered in debug source sets only; release builds leave this null.
 */
fun interface NotificationDebugHook {
    fun onNotificationReceived(context: Context, sbn: StatusBarNotification)
}
