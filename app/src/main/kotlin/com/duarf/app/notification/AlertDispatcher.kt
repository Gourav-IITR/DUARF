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
