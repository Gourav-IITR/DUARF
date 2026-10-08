package com.duarf.app.notification

import androidx.annotation.StringRes
import com.duarf.app.R
import com.duarf.engine.model.Reason

/** What a warning tells the user to do, chosen from the most important reason that has a clear action. */
enum class WarningAction(@StringRes val textRes: Int) {
    FILE(R.string.warn_action_file),
    CODE(R.string.warn_action_code),
    MONEY(R.string.warn_action_money),
    LINK(R.string.warn_action_link),
    GENERIC(R.string.warn_action_generic);

    companion object {
        private val FILE_SIGNALS = setOf("L01", "A02")
        private val CODE_SIGNALS = setOf("A01", "A04", "A05")
        private val MONEY_SIGNALS = setOf("A03", "A09")
        private val LINK_SIGNALS = setOf("A07")

        /** [reasons] arrive ordered by weight, so the first reason with an action wins. */
        fun forReasons(reasons: List<Reason>): WarningAction {
            for (reason in reasons) {
                val id = reason.signalId
                when {
                    id in FILE_SIGNALS -> return FILE
                    id in CODE_SIGNALS -> return CODE
                    id in MONEY_SIGNALS -> return MONEY
                    id.startsWith("L") || id in LINK_SIGNALS -> return LINK
                }
            }
            return GENERIC
        }
    }
}
