// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.notification

import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.Verdict

interface AlertDispatcher {
    suspend fun dispatchAlert(
        alertId: Long,
        fingerprint: String,
        senderDisplay: String?,
        verdict: Verdict,
        sourceApp: SourceApp = SourceApp.WHATSAPP
    )
}
