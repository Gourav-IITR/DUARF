package com.duarf.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.app.ui.UiState
import com.duarf.engine.model.Sensitivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: UiState,
    onBack: () -> Unit,
    onUpdateSensitivity: (Sensitivity) -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdateRetention: (Int) -> Unit,
    onUpdateGroupAlerts: (Boolean) -> Unit,
    onNavigatePrivacyProof: () -> Unit,
    onNavigateAbout: () -> Unit,
    onDeleteAllData: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAllData()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Language selection (§8, §13.3)
            Column {
                Text(
                    text = stringResource(R.string.setting_language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.preferences.languageCode == "en",
                        onClick = { onUpdateLanguage("en") },
                        label = { Text("English") }
                    )
                    FilterChip(
                        selected = uiState.preferences.languageCode == "hi",
                        onClick = { onUpdateLanguage("hi") },
                        label = { Text("हिंदी (Hindi)") }
                    )
                }
            }

            HorizontalDivider()

            // Sensitivity setting (§10, §13.3)
            Column {
                Text(
                    text = stringResource(R.string.setting_sensitivity),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                SensitivityOption(
                    title = stringResource(R.string.sensitivity_balanced),
                    selected = uiState.preferences.sensitivity == Sensitivity.BALANCED,
                    onSelect = { onUpdateSensitivity(Sensitivity.BALANCED) }
                )
                SensitivityOption(
                    title = stringResource(R.string.sensitivity_high),
                    selected = uiState.preferences.sensitivity == Sensitivity.HIGH,
                    onSelect = { onUpdateSensitivity(Sensitivity.HIGH) }
                )
                SensitivityOption(
                    title = stringResource(R.string.sensitivity_low),
                    selected = uiState.preferences.sensitivity == Sensitivity.LOW,
                    onSelect = { onUpdateSensitivity(Sensitivity.LOW) }
                )
            }

            HorizontalDivider()

            // Group alerts toggle (§12)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_group_alerts),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Alert on group chat messages (disabled by default to reduce noise)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.preferences.groupAlerts,
                    onCheckedChange = onUpdateGroupAlerts
                )
            }

            HorizontalDivider()

            // Retention period picker (§12)
            Column {
                Text(
                    text = stringResource(R.string.setting_retention),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.preferences.retentionDays == 7,
                        onClick = { onUpdateRetention(7) },
                        label = { Text(stringResource(R.string.retention_7_days)) }
                    )
                    FilterChip(
                        selected = uiState.preferences.retentionDays == 30,
                        onClick = { onUpdateRetention(30) },
                        label = { Text(stringResource(R.string.retention_30_days)) }
                    )
                    FilterChip(
                        selected = uiState.preferences.retentionDays == 90,
                        onClick = { onUpdateRetention(90) },
                        label = { Text(stringResource(R.string.retention_90_days)) }
                    )
                }
            }

            HorizontalDivider()

            // Navigation links: Proof of Privacy and About (§13.3)
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_privacy_proof)) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onNavigatePrivacyProof)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_about)) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onNavigateAbout)
            )

            HorizontalDivider()

            // Delete all data (§12)
            Button(
                onClick = { showDeleteDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.setting_delete_all))
            }
        }
    }
}

@Composable
private fun SensitivityOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}
