// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.capture.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Debug-only ContentProvider that automatically installs the NotificationRecorder hook on app startup (§16.3).
 * Never included in release builds.
 */
class NotificationRecorderInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { ctx ->
            NotificationRecorder.install()
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
