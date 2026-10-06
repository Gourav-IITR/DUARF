package com.duarf.data.log

/**
 * Audited logging wrapper conforming to Section 14 invariant:
 * Accepts only enumerated event codes and numeric values. NEVER message content or personal data.
 */
object SafeLog {
    enum class EventCode(val code: Int) {
        DB_ALERT_SAVED(200),
        DB_RETENTION_PURGED(201),
        DB_ALL_WIPED(202),
        CRYPTO_KEY_GENERATED(203),
        ERROR_CRYPTO(299),
        ERROR_ENGINE_INIT(300),
        ERROR_ENGINE_ANALYSIS(301)
    }

    fun event(event: EventCode, count: Long = 0) {
        // Safe numeric logging only
    }
}
