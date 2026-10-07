package com.duarf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.Verdict

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckResultScreen(
    messageText: String,
    verdict: Verdict,
    onBack: () -> Unit
) {
    val bannerBg = when (verdict.level) {
        AlertLevel.DANGER -> Color(0xFFD32F2F)
        AlertLevel.CAUTION -> Color(0xFFF57C00)
        AlertLevel.NONE -> Color(0xFF388E3C)
    }

    val bannerText = when (verdict.level) {
        AlertLevel.DANGER -> stringResource(R.string.level_danger)
        AlertLevel.CAUTION -> stringResource(R.string.level_caution)
        AlertLevel.NONE -> stringResource(R.string.level_safe)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check Result") },
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
        ) {
            // Level banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bannerBg)
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = bannerText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Text analyzed with highlights
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.label_message_content),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = buildHighlightedText(messageText, verdict.highlights),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                val displayReasons = if (verdict.level == AlertLevel.NONE) {
                    verdict.reasons.filter { !it.signalId.startsWith("S") }
                } else {
                    verdict.reasons
                }

                if (displayReasons.isNotEmpty()) {
                    val heading = if (verdict.level == AlertLevel.NONE) {
                        stringResource(R.string.label_checked)
                    } else {
                        stringResource(R.string.label_reasons)
                    }
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    displayReasons.forEach { reason ->
                        ReasonItemCard(reason = reason)
                    }
                }

                if (verdict.level != AlertLevel.NONE) {
                    val isSoftCaution = verdict.level == AlertLevel.CAUTION &&
                            verdict.reasons.none { it.signalId in setOf("L01", "L10", "L11", "A01", "A02", "A04") }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.label_advice),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            if (isSoftCaution) {
                                Text(stringResource(R.string.advice_caution_soft), style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("1. " + stringResource(R.string.advice_1), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(4.dp))
                                Text("2. " + stringResource(R.string.advice_2), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(4.dp))
                                Text("3. " + stringResource(R.string.advice_3), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Beta language disclaimer footer (§19, M5 done-when)
                BetaLanguageFooter(text = messageText)
            }
        }
    }
}
