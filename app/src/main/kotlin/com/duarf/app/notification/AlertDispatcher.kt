package com.duarf.app.notification

import com.duarf.engine.model.Verdict

interface AlertDispatcher {
    fun dispatchAlert(
        alertId: Long,
        fingerprint: String,
        senderDisplay: String?,
        verdict: Verdict
    )
}
