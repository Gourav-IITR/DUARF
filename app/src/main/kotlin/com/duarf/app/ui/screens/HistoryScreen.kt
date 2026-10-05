package com.duarf.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.data.repo.DecryptedAlert
import com.duarf.engine.model.AlertLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    alerts: List<DecryptedAlert>,
    onBack: () -> Unit,
    onAlertClick: (Long) -> Unit,
    onDismissAlert: (Long) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<AlertLevel?>(null) }

    val filteredAlerts = remember(alerts, selectedFilter) {
        if (selectedFilter == null) alerts
        else alerts.filter { it.level == selectedFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
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
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                FilterChip(
                    selected = selectedFilter == AlertLevel.DANGER,
                    onClick = { selectedFilter = AlertLevel.DANGER },
                    label = { Text(stringResource(R.string.filter_danger)) }
                )
                FilterChip(
                    selected = selectedFilter == AlertLevel.CAUTION,
                    onClick = { selectedFilter = AlertLevel.CAUTION },
                    label = { Text(stringResource(R.string.filter_caution)) }
                )
            }

            if (filteredAlerts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_history),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAlerts, key = { it.id }) { alert ->
                        AlertItemRow(
                            alert = alert,
                            onClick = { onAlertClick(alert.id) }
                        )
                    }
                }
            }
        }
    }
}
