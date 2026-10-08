package com.duarf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.app.notification.WarningAction
import com.duarf.app.ui.components.*
import com.duarf.app.ui.theme.style
import com.duarf.data.repo.FamilyContact
import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.Verdict

/** Same layout as an alert, including the "Looks safe, stay alert" state for low scores (§13.3). */
@Composable
fun CheckResultScreen(
    messageText: String,
    verdict: Verdict,
    familyContact: FamilyContact?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val subtitle = when (verdict.level) {
        AlertLevel.DANGER -> stringResource(WarningAction.forReasons(verdict.reasons).textRes) + "."
        AlertLevel.CAUTION -> verdict.reasons.firstOrNull()?.let { reasonTitle(context, it) }
        AlertLevel.NONE -> stringResource(R.string.level_safe_sub)
    }
    // A safe result does not list sender signals: they only matter alongside something else.
    val displayReasons = if (verdict.level == AlertLevel.NONE) {
        verdict.reasons.filter { !it.signalId.startsWith("S") }
    } else {
        verdict.reasons
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            VerdictHeader(
                level = verdict.level,
                title = levelShortLabel(verdict.level),
                subtitle = subtitle,
                meta = stringResource(R.string.check_result_title),
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.label_message_content),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HighlightedMessage(
                    text = messageText,
                    highlights = verdict.highlights,
                    reasons = displayReasons,
                    level = verdict.level,
                    sender = null
                )

                if (displayReasons.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle(
                        stringResource(if (verdict.level == AlertLevel.NONE) R.string.label_checked else R.string.label_reasons)
                    )
                    displayReasons.forEachIndexed { i, reason ->
                        ReasonCard(number = i + 1, reason = reason, level = verdict.level)
                    }
                }

                if (verdict.level != AlertLevel.NONE) {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle(stringResource(R.string.label_advice))
                    WhatToDoCard(softCaution = isSoftCaution(verdict.level, verdict.reasons), familyContact = familyContact)
                }

                BetaLanguageFooter(text = messageText)
                PrivateFooter()
            }
        }
        StatusBarScrim(verdict.level.style().strong)
    }
}
