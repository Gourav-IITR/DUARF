// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duarf.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    isModelLoaded: Boolean = false,
    modelVersion: Int? = null,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DUARF Sentinel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Version 1.0.0 (Release Build)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Engine: Aho-Corasick + Rule Fusion", style = MaterialTheme.typography.bodySmall)
                    if (isModelLoaded) {
                        Text("Model: loaded v${modelVersion ?: 1}", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Model: not loaded (rules only)", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Packs: Rules v1, Tier 1 (en, hi, hi-Latn)", style = MaterialTheme.typography.bodySmall)
                    Text("Storage: Android Keystore AES-256-GCM + Room", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How DUARF Works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "DUARF runs an entirely local scam detection engine on your phone. " +
                                "Incoming WhatsApp notifications are analyzed within milliseconds for phishing URLs, " +
                                "malicious APK files, digital arrest threats, electricity cutoff lures, and OTP asks. " +
                                "Messages that aren't flagged are analysed in memory and discarded. " +
                                "Flagged alerts are saved only on your phone, and you can delete them anytime.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Emergency Contacts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("National Cyber Crime Helpline: 1930", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("National Cyber Crime Portal: cybercrime.gov.in", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
