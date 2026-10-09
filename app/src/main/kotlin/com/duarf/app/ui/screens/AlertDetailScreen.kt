// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.app.notification.WarningAction
import com.duarf.app.ui.components.*
import com.duarf.app.ui.theme.style
import com.duarf.data.repo.DecryptedAlert
import com.duarf.data.repo.FamilyContact
import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.Reason

private val HARD_SIGNALS = setOf("L01", "L10", "L11", "A01", "A02", "A04")

/** A Caution with no hard signal gets the softer "verify first" advice. */
fun isSoftCaution(level: AlertLevel, reasons: List<Reason>): Boolean =
    level == AlertLevel.CAUTION && reasons.none { it.signalId in HARD_SIGNALS }

@Composable
fun AlertDetailScreen(
    alert: DecryptedAlert,
    familyContact: FamilyContact?,
    onBack: () -> Unit,
    onFeedback: (Boolean) -> Unit,
    onTrustSender: () -> Unit
) {
    val context = LocalContext.current
    var feedbackGiven by remember { mutableStateOf(alert.userFeedback) }
    var showShare by remember { mutableStateOf(false) }

    val isDanger = alert.level == AlertLevel.DANGER
    val subtitle = if (isDanger) {
        stringResource(WarningAction.forReasons(alert.reasons).textRes) + "."
    } else {
        alert.reasons.firstOrNull()?.let { reasonTitle(context, it) }
    }
    val meta = listOfNotNull(
        sourceLabel(alert.app),
        alert.senderDisplay,
        formatAlertTime(context, alert.createdAt).ifEmpty { null }
    ).joinToString(" · ")
    val shareText = stringResource(
        R.string.share_warning_text,
        alert.reasons.firstOrNull()?.let { reasonTitle(context, it) } ?: levelShortLabel(alert.level)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            VerdictHeader(
                level = alert.level,
                title = levelShortLabel(alert.level),
                subtitle = subtitle,
                meta = meta,
                onBack = onBack,
                onShare = { showShare = true }
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
                    text = alert.text,
                    highlights = alert.highlights,
                    reasons = alert.reasons,
                    level = alert.level,
                    sender = alert.senderDisplay
                )

                Spacer(Modifier.height(8.dp))
                SectionTitle(stringResource(R.string.label_reasons))
                alert.reasons.forEachIndexed { i, reason ->
                    ReasonCard(number = i + 1, reason = reason, level = alert.level)
                }

                Spacer(Modifier.height(8.dp))
                SectionTitle(stringResource(R.string.label_advice))
                WhatToDoCard(softCaution = isSoftCaution(alert.level, alert.reasons), familyContact = familyContact)

                // Feedback stays on this device only (§13.3).
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.detail_feedback_title), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            feedbackGiven = "SAFE"
                            onFeedback(false)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        colors = if (feedbackGiven == "SAFE") {
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text(stringResource(R.string.btn_this_is_safe))
                    }
                    Button(
                        onClick = {
                            feedbackGiven = "SCAM"
                            onFeedback(true)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (feedbackGiven == "SCAM") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.btn_this_is_scam))
                    }
                }
                TextButton(
                    onClick = onTrustSender,
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_trust_sender))
                }

                BetaLanguageFooter(text = alert.text)
                PrivateFooter()
            }
        }
        StatusBarScrim(alert.level.style().strong)
    }

    // "Share this warning" shows the exact text first; the original message is never included (§13.3, invariant 9).
    if (showShare) {
        AlertDialog(
            onDismissRequest = { showShare = false },
            title = { Text(stringResource(R.string.btn_share_warning)) },
            text = { Text(shareText) },
            confirmButton = {
                TextButton(onClick = {
                    showShare = false
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }) {
                    Text(stringResource(R.string.btn_share))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShare = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
fun BetaLanguageFooter(text: String, modifier: Modifier = Modifier) {
    val langInfo = remember(text) {
        com.duarf.engine.normalize.LanguageScriptDetector.detectLanguageInfo(text)
    }
    if (langInfo != null && langInfo.isBeta) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "${langInfo.displayName} " + stringResource(R.string.beta_label_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.beta_disclaimer_testing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
