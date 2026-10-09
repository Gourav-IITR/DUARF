// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.app.notification.WarningAction
import com.duarf.app.ui.components.*
import com.duarf.app.ui.theme.DuarfColors
import com.duarf.data.repo.DecryptedAlert
import com.duarf.data.repo.FamilyContact

/**
 * The first thing a Danger warning opens: what to do, the one reason that matters most, and the
 * people to call. Everything else is one tap away on the full detail screen.
 */
@Composable
fun StopScreen(
    alert: DecryptedAlert,
    familyContact: FamilyContact?,
    onDone: () -> Unit,
    onDetails: () -> Unit
) {
    val context = LocalContext.current
    val action = stringResource(WarningAction.forReasons(alert.reasons).textRes)
    val topReason = alert.reasons.firstOrNull()
    val meta = listOfNotNull(
        sourceLabel(alert.app),
        alert.senderDisplay,
        formatAlertTime(context, alert.createdAt).ifEmpty { null }
    ).joinToString(" · ")

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                color = DuarfColors.Danger,
                contentColor = Color.White,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding().padding(start = 4.dp, end = 4.dp, bottom = 24.dp)) {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.stop_ok))
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stat_duarf),
                                contentDescription = null,
                                tint = DuarfColors.Danger,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.stop_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(text = "$action.", style = MaterialTheme.typography.titleLarge)
                        if (meta.isNotBlank()) {
                            Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (topReason != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.stop_why) + " " + reasonTitle(context, topReason),
                                style = MaterialTheme.typography.titleMedium
                            )
                            DomainCompare(topReason)
                        }
                    }
                }

                if (familyContact != null) {
                    CallButton(
                        title = stringResource(R.string.action_call_contact, familyContact.name),
                        subtitle = stringResource(R.string.stop_call_contact_sub),
                        filled = true,
                        onClick = { openDialer(context, familyContact.number) }
                    )
                }
                CallButton(
                    title = stringResource(R.string.btn_call_1930),
                    subtitle = stringResource(R.string.stop_call_1930_sub),
                    filled = false,
                    color = DuarfColors.Danger,
                    onClick = { openDialer(context, "1930") }
                )
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Text(stringResource(R.string.stop_ok), style = MaterialTheme.typography.titleMedium)
                }
                TextButton(
                    onClick = onDetails,
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.stop_details), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        StatusBarScrim(DuarfColors.Danger)
    }
}
