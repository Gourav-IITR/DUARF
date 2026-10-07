package com.duarf.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.app.ui.UiState
import com.duarf.data.repo.DecryptedAlert
import com.duarf.engine.model.AlertLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: UiState,
    onNavigateSettings: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigatePrivacyProof: () -> Unit,
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
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "DUARF",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigatePrivacyProof) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.setting_privacy_proof)
                        )
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Protection status card (§13.3)
            item {
                ProtectionStatusCard(
                    isEnabled = uiState.listenerHealth.isEnabled,
                    isConnected = uiState.listenerHealth.isConnected,
                    lastEventMillis = uiState.listenerHealth.lastEventMillis
                )
            }

            // 2. Weekly statistics card (§13.3)
            item {
                WeeklyStatsCard(uiState = uiState)
            }

            // 3. "Check a message" in-app paste box (§13.3)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.card_check_message),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            placeholder = { Text(stringResource(R.string.hint_paste_message)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp),
                            maxLines = 5
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isUnknownNumber,
                                onCheckedChange = { isUnknownNumber = it }
                            )
                            Text(
                                text = stringResource(R.string.toggle_unknown_number),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onCheckMessage(pasteText, isUnknownNumber)
                            },
                            enabled = pasteText.isNotBlank(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_analyze))
                        }
                    }
                }
            }

            // 4. Recent Alerts (§13.3)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.title_recent_alerts),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.recentAlerts.isNotEmpty()) {
                        TextButton(onClick = onNavigateHistory) {
                            Text(stringResource(R.string.view_all))
                        }
                    }
                }
            }

            if (uiState.recentAlerts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_recent_alerts),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.recentAlerts.take(5)) { alert ->
                    AlertItemRow(alert = alert, onClick = { onAlertClick(alert.id) })
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun ProtectionStatusCard(
    isEnabled: Boolean,
    isConnected: Boolean,
    lastEventMillis: Long
) {
    val context = LocalContext.current
    val isProtected = isEnabled && isConnected
    val cardColor = if (isProtected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val contentColor = if (isProtected) Color(0xFF2E7D32) else Color(0xFFC62828)

    val cardModifier = if (!isProtected) {
        Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
    } else {
        Modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isProtected) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isProtected) {
                        stringResource(R.string.status_protected)
                    } else {
                        stringResource(R.string.status_action_needed)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = if (isProtected) {
                        val lastCheckStr = if (lastEventMillis > 0) {
                            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastEventMillis))
                            " • Last event $timeStr"
                        } else ""
                        stringResource(R.string.status_listener_running) + lastCheckStr
                    } else if (!isEnabled) {
                        stringResource(R.string.status_listener_disabled)
                    } else {
                        stringResource(R.string.status_listener_paused)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
                if (!isProtected) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.status_tap_to_enable),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.card_weekly_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatCounter(count = totalChecked, label = stringResource(R.string.stat_checked), color = MaterialTheme.colorScheme.primary)
                StatCounter(count = totalCautions, label = stringResource(R.string.stat_cautions), color = Color(0xFFF57C00))
                StatCounter(count = totalDangers, label = stringResource(R.string.stat_dangers), color = Color(0xFFD32F2F))
            }
        }
    }
}

@Composable
private fun StatCounter(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AlertItemRow(
    alert: DecryptedAlert,
    onClick: () -> Unit
) {
    val isDanger = alert.level == AlertLevel.DANGER
    val badgeColor = if (isDanger) Color(0xFFFFCDD2) else Color(0xFFFFE0B2)
    val badgeTextColor = if (isDanger) Color(0xFFB71C1C) else Color(0xFFE65100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    text = alert.level.name,
                    color = badgeTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.senderDisplay ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = alert.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
