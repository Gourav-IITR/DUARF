// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.app.locale.AppLanguage
import com.duarf.app.ui.UiState
import com.duarf.app.ui.components.DuarfMark
import com.duarf.app.ui.components.LevelTile
import com.duarf.app.ui.components.formatAlertTime
import com.duarf.app.ui.components.levelShortLabel
import com.duarf.app.ui.theme.DuarfColors
import com.duarf.app.ui.theme.style
import com.duarf.data.repo.DecryptedAlert
import com.duarf.engine.model.AlertLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: UiState,
    onNavigateSettings: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigatePrivacyProof: () -> Unit,
    onNavigateLanguage: () -> Unit,
    onAlertClick: (Long) -> Unit,
    onCheckMessage: (String, Boolean) -> Unit
) {
    var pasteText by remember { mutableStateOf("") }
    var isUnknownNumber by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DuarfMark()
                        Spacer(Modifier.width(10.dp))
                        Text(text = stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    // Language switcher, always visible: shows the current language in its own script.
                    val currentLanguage = AppLanguage.find(AppLanguage.current(LocalContext.current))
                    val languageDescription = stringResource(R.string.setting_language) + ": " + currentLanguage.nativeName
                    TextButton(
                        onClick = onNavigateLanguage,
                        modifier = Modifier.semantics { contentDescription = languageDescription }
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(currentLanguage.nativeName)
                    }
                    IconButton(onClick = onNavigatePrivacyProof) {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.setting_privacy_proof))
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ProtectionStatusCard(
                    isEnabled = uiState.listenerHealth.isEnabled,
                    isConnected = uiState.listenerHealth.isConnected,
                    lastEventMillis = uiState.listenerHealth.lastEventMillis,
                    onPrivacyClick = onNavigatePrivacyProof
                )
            }
            item { WeeklyStatsCard(uiState = uiState) }
            item {
                CheckMessageCard(
                    text = pasteText,
                    onTextChange = { pasteText = it },
                    isUnknownNumber = isUnknownNumber,
                    onUnknownChange = { isUnknownNumber = it },
                    onCheck = { onCheckMessage(pasteText, isUnknownNumber) }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.title_recent_alerts), style = MaterialTheme.typography.titleMedium)
                    if (uiState.recentAlerts.isNotEmpty()) {
                        TextButton(onClick = onNavigateHistory) {
                            Text(stringResource(R.string.view_all))
                        }
                    }
                }
            }
            if (uiState.recentAlerts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_recent_alerts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(uiState.recentAlerts.take(5), key = { it.id }) { alert ->
                    AlertItemRow(alert = alert, onClick = { onAlertClick(alert.id) })
                }
            }
        }
    }
}

@Composable
fun ProtectionStatusCard(
    isEnabled: Boolean,
    isConnected: Boolean,
    lastEventMillis: Long,
    onPrivacyClick: () -> Unit
) {
    val context = LocalContext.current
    val isProtected = isEnabled && isConnected
    val level = if (isProtected) AlertLevel.NONE else AlertLevel.DANGER
    val style = level.style()

    Surface(color = style.soft, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(52.dp).background(style.strong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isProtected) Icons.Default.VerifiedUser else Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(if (isProtected) R.string.status_protected else R.string.status_action_needed),
                        style = MaterialTheme.typography.headlineSmall,
                        color = style.onSoft
                    )
                    val detail = when {
                        isProtected && lastEventMillis > 0 -> stringResource(R.string.status_listener_running) + ". " +
                            stringResource(R.string.status_last_checked, formatAlertTime(context, lastEventMillis))
                        isProtected -> stringResource(R.string.status_listener_running)
                        !isEnabled -> stringResource(R.string.status_listener_disabled)
                        else -> stringResource(R.string.status_listener_paused)
                    }
                    Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = style.onSoft)
                }
            }
            if (isProtected) {
                // The privacy promise sits on the status card, one tap from the proof (§13.3).
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onPrivacyClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = DuarfColors.Safe, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(R.string.home_privacy_line),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = style.strong)
                ) {
                    Text(stringResource(R.string.status_turn_on))
                }
            }
        }
    }
}

@Composable
fun WeeklyStatsCard(uiState: UiState) {
    val totalChecked = uiState.weeklyStats.sumOf { it.messagesChecked }
    val totalCautions = uiState.weeklyStats.sumOf { it.cautions }
    val totalDangers = uiState.weeklyStats.sumOf { it.dangers }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.card_weekly_stats), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCounter(totalChecked, stringResource(R.string.stat_checked), null, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                StatCounter(totalCautions, stringResource(R.string.stat_cautions), AlertLevel.CAUTION.style().icon, DuarfColors.Caution, Modifier.weight(1f))
                StatCounter(totalDangers, stringResource(R.string.stat_dangers), AlertLevel.DANGER.style().icon, DuarfColors.Danger, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCounter(count: Int, label: String, icon: ImageVector?, color: Color, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(text = "%,d".format(count), style = MaterialTheme.typography.headlineSmall, color = color)
        }
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CheckMessageCard(
    text: String,
    onTextChange: (String) -> Unit,
    isUnknownNumber: Boolean,
    onUnknownChange: (Boolean) -> Unit,
    onCheck: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.card_check_message), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.hint_paste_message)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = isUnknownNumber, onValueChange = onUnknownChange, role = Role.Checkbox),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isUnknownNumber, onCheckedChange = null)
                Spacer(Modifier.width(10.dp))
                Text(text = stringResource(R.string.toggle_unknown_number), style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onCheck,
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_analyze))
            }
        }
    }
}

/** One alert in a list: level icon and word, time, sender and the start of the message. */
@Composable
fun AlertItemRow(
    alert: DecryptedAlert,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LevelTile(alert.level)
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = levelShortLabel(alert.level),
                        style = MaterialTheme.typography.titleSmall,
                        color = alert.level.style().strong,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatAlertTime(context, alert.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = listOfNotNull(alert.senderDisplay ?: stringResource(R.string.unknown_sender), alert.text).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
