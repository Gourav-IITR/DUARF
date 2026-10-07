package com.duarf.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.data.repo.DecryptedAlert
import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.Reason
import com.duarf.engine.model.TextSpan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailScreen(
    alert: DecryptedAlert,
    onBack: () -> Unit,
    onFeedback: (Boolean) -> Unit,
    onTrustSender: () -> Unit
) {
    val context = LocalContext.current
    var feedbackGiven by remember { mutableStateOf(alert.userFeedback) }

    val isDanger = alert.level == AlertLevel.DANGER
    val bannerBg = if (isDanger) Color(0xFFD32F2F) else Color(0xFFF57C00)
    val bannerText = if (isDanger) stringResource(R.string.level_danger) else stringResource(R.string.level_caution)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(alert.category.name.replace("_", " ")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "DUARF Security Alert: Detected likely WhatsApp scam (${alert.category.name.replace('_', ' ')}). Please do not open suspicious links or share OTPs!"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Warning"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
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
            // Level banner (§13.3)
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
                // Sender info
                if (!alert.senderDisplay.isNullOrEmpty()) {
                    val label = if (alert.app.isSms) {
                        "SMS from ${alert.senderDisplay}"
                    } else {
                        "From: ${alert.senderDisplay}"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Message content with highlights (§11, §13.3)
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
                            text = buildHighlightedText(alert.text, alert.highlights),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Reasons section (§11)
                Text(
                    text = stringResource(R.string.label_reasons),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                alert.reasons.forEach { reason ->
                    ReasonItemCard(reason = reason)
                }

                // Actionable advice (§13.3)
                val isSoftCaution = alert.level == AlertLevel.CAUTION &&
                        alert.reasons.none { it.signalId in setOf("L01", "L10", "L11", "A01", "A02", "A04") }
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

                // User Feedback Buttons (§13.3)
                Text(
                    text = "Help improve DUARF:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            feedbackGiven = "SAFE"
                            onFeedback(false)
                        },
                        modifier = Modifier.weight(1f),
                        colors = if (feedbackGiven == "SAFE") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_this_is_safe))
                    }
                    Button(
                        onClick = {
                            feedbackGiven = "SCAM"
                            onFeedback(true)
                        },
                        modifier = Modifier.weight(1f),
                        colors = if (feedbackGiven == "SCAM") ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                    ) {
                        Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_this_is_scam))
                    }
                }

                // Trust sender option
                TextButton(
                    onClick = onTrustSender,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.btn_trust_sender))
                }

                // Beta language disclaimer footer (§19, M5 done-when)
                BetaLanguageFooter(text = alert.text)
            }
        }
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
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "${langInfo.displayName} (Beta)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "Scam detection in this language is currently in Beta and still being tested.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ReasonItemCard(reason: Reason) {
    val context = LocalContext.current
    val titleRes = context.resources.getIdentifier(reason.titleKey, "string", context.packageName)
    val detailRes = context.resources.getIdentifier(reason.detailKey, "string", context.packageName)

    val title = if (titleRes != 0) context.getString(titleRes) else reason.signalId
    val detail = if (detailRes != 0) context.getString(detailRes) else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = reason.signalId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (reason.args.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                val argsText = reason.args.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                Text(
                    text = argsText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

fun buildHighlightedText(text: String, highlights: List<TextSpan>): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        for (span in highlights) {
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(0, text.length)
            if (start < end) {
                addStyle(
                    style = SpanStyle(
                        background = Color(0xFFFFF176),
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }
}
