package com.duarf.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duarf.app.R
import com.duarf.app.ui.theme.DuarfColors
import com.duarf.app.ui.theme.style
import com.duarf.data.repo.FamilyContact
import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.Reason
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.TextSpan

/** The DUARF mark: the palm from the app icon on an indigo tile. */
@Composable
fun DuarfMark(size: Int = 32) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(DuarfColors.Indigo, RoundedCornerShape((size * 0.3).dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_stat_duarf),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.62).dp)
        )
    }
}

/** Icon on a tinted tile, so a level reads by shape as well as colour. */
@Composable
fun LevelTile(level: AlertLevel, size: Int = 44) {
    val style = level.style()
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(style.soft, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(style.icon, contentDescription = null, tint = style.strong, modifier = Modifier.size((size * 0.55).dp))
    }
}

@Composable
fun levelShortLabel(level: AlertLevel): String = when (level) {
    AlertLevel.DANGER -> stringResource(R.string.level_danger_short)
    AlertLevel.CAUTION -> stringResource(R.string.level_caution_short)
    AlertLevel.NONE -> stringResource(R.string.level_safe_short)
}

@Composable
private fun levelChipLabel(level: AlertLevel): String = when (level) {
    AlertLevel.DANGER -> stringResource(R.string.filter_danger)
    AlertLevel.CAUTION -> stringResource(R.string.filter_caution)
    AlertLevel.NONE -> stringResource(R.string.level_safe_short)
}

/** WhatsApp, SMS and so on, for the "where it came from" line. */
fun sourceLabel(app: SourceApp): String? = when {
    app == SourceApp.WHATSAPP -> "WhatsApp"
    app == SourceApp.WHATSAPP_BUSINESS -> "WhatsApp Business"
    app.isSms -> "SMS"
    else -> null
}

fun formatAlertTime(context: Context, millis: Long): String {
    if (millis <= 0) return ""
    val flags = if (DateUtils.isToday(millis)) {
        DateUtils.FORMAT_SHOW_TIME
    } else {
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    }
    return DateUtils.formatDateTime(context, millis, flags)
}

fun reasonTitle(context: Context, reason: Reason): String {
    val res = context.resources.getIdentifier(reason.titleKey, "string", context.packageName)
    return if (res != 0) context.getString(res) else reason.signalId
}

fun reasonDetail(context: Context, reason: Reason): String {
    val res = context.resources.getIdentifier(reason.detailKey, "string", context.packageName)
    return if (res != 0) context.getString(res) else ""
}

/** Opens the dialer or browser on the user's tap only; nothing from the message goes with it (§13.3). */
fun openDialer(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    } catch (_: ActivityNotFoundException) {
    }
}

fun openCybercrimePortal(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in")))
    } catch (_: ActivityNotFoundException) {
    }
}

/** The coloured block at the top of an alert: level chip, big verdict, one line on what to do, and where it came from. */
@Composable
fun VerdictHeader(
    level: AlertLevel,
    title: String,
    subtitle: String?,
    meta: String?,
    onBack: () -> Unit,
    onShare: (() -> Unit)? = null
) {
    val style = level.style()
    Surface(
        color = style.strong,
        contentColor = style.onStrong,
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding().padding(start = 4.dp, end = 4.dp, bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                }
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.btn_share_warning))
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(color = Color.White, contentColor = style.strong, shape = RoundedCornerShape(50)) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(style.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = levelChipLabel(level).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(text = title, style = MaterialTheme.typography.headlineMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                }
                if (!meta.isNullOrBlank()) {
                    Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = style.onStrong.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/**
 * The message as received, with the evidence highlighted. Each highlight that belongs to one of
 * the numbered reasons below carries that reason's number.
 */
@Composable
fun HighlightedMessage(
    text: String,
    highlights: List<TextSpan>,
    reasons: List<Reason>,
    level: AlertLevel,
    sender: String?
) {
    val style = level.style()
    val markColor = if (level == AlertLevel.DANGER) DuarfColors.DangerMark else style.soft
    val numbered = reasons.mapIndexedNotNull { i, r -> r.evidence?.let { it to i + 1 } }
    val spans = (highlights + numbered.map { it.first })
        .map { (it.start.coerceIn(0, text.length)) to (it.end.coerceIn(0, text.length)) }
        .filter { it.first < it.second }
        .sortedBy { it.first }
    val annotated = buildAnnotatedString {
        var pos = 0
        for ((start, end) in spans) {
            if (start < pos) continue
            append(text.substring(pos, start))
            withStyle(SpanStyle(background = markColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                append(text.substring(start, end))
            }
            numbered.filter { (span, _) -> span.start >= start && span.end <= end }
                .forEach { (_, n) -> appendInlineContent("marker$n", "[$n]") }
            pos = end
        }
        append(text.substring(pos))
    }
    val inline = numbered.associate { (_, n) ->
        "marker$n" to InlineTextContent(Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)) {
            NumberDot(n, style.strong, size = 18)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!sender.isNullOrBlank()) {
                Text(
                    text = sender,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = annotated, inlineContent = inline, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun NumberDot(number: Int, color: Color, size: Int = 26) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            color = Color.White,
            fontSize = (size * 0.55).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** "Link goes to X" next to "SBI's real site Y", when the engine named both (§11). */
@Composable
fun DomainCompare(reason: Reason) {
    val domain = reason.args["domain"]?.takeIf { it.isNotBlank() } ?: return
    val official = reason.args["officialDomain"]?.takeIf { it.isNotBlank() }
    val brand = reason.args["brand"]?.takeIf { it.isNotBlank() }?.let(::brandDisplayName)
    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CompareLine(Icons.Default.Close, DuarfColors.Danger, stringResource(R.string.reason_link_goes_to), domain)
            if (official != null && brand != null) {
                CompareLine(Icons.Default.Check, DuarfColors.Safe, stringResource(R.string.reason_real_site, brand), official)
            }
        }
    }
}

private val BRAND_DISPLAY_NAMES = mapOf(
    "phonepe" to "PhonePe", "paytm" to "Paytm", "kotak" to "Kotak", "jio" to "Jio",
    "airtel" to "Airtel", "parivahan" to "Parivahan", "bank of baroda" to "Bank of Baroda",
    "pm mudra" to "PM Mudra", "pm kisan" to "PM Kisan"
)

/** brands.json keeps names in lower case; most are acronyms (sbi, uidai, msedcl), so those are shown in capitals. */
fun brandDisplayName(raw: String): String {
    BRAND_DISPLAY_NAMES[raw]?.let { return it }
    if (raw.any { it.code > 127 }) return raw
    val words = raw.split(' ')
    return if (words.size == 1) {
        raw.uppercase()
    } else {
        words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}

@Composable
private fun CompareLine(icon: ImageVector, color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** One numbered reason: plain title, why it matters, and the evidence comparison when there is one. */
@Composable
fun ReasonCard(number: Int, reason: Reason, level: AlertLevel) {
    val context = LocalContext.current
    val detail = reasonDetail(context, reason)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberDot(number, level.style().strong)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(text = reasonTitle(context, reason), style = MaterialTheme.typography.titleMedium)
                if (detail.isNotEmpty()) {
                    Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DomainCompare(reason)
            }
        }
    }
}

/**
 * "What to do now": the same three actions on every warning (§13.3), plus the family contact
 * when one is saved.
 */
@Composable
fun WhatToDoCard(softCaution: Boolean, familyContact: FamilyContact?) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (familyContact != null) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    CallButton(
                        title = stringResource(R.string.action_call_contact, familyContact.name),
                        subtitle = stringResource(R.string.stop_call_contact_sub),
                        filled = true,
                        onClick = { openDialer(context, familyContact.number) }
                    )
                }
            }
            if (softCaution) {
                AdviceRow(Icons.Default.Info, stringResource(R.string.advice_caution_soft))
            } else {
                AdviceRow(Icons.Default.Cancel, stringResource(R.string.advice_1))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AdviceRow(Icons.Default.Block, stringResource(R.string.advice_2))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text(text = stringResource(R.string.advice_paid_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.advice_paid_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openDialer(context, "1930") }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.btn_call_1930))
                        }
                        OutlinedButton(onClick = { openCybercrimePortal(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("cybercrime.gov.in")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdviceRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A large two-line call button, used for the family contact and for 1930. */
@Composable
fun CallButton(
    title: String,
    subtitle: String,
    filled: Boolean,
    onClick: () -> Unit,
    color: Color = DuarfColors.Indigo
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 72.dp)
    val padding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            content = content
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            contentPadding = padding,
            border = BorderStroke(2.dp, color),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            content = content
        )
    }
}

@Composable
fun PrivateFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.detail_footer_private),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Keeps scrolled content from running under the clock: a strip in the header's colour behind the status bar. */
@Composable
fun BoxScope.StatusBarScrim(color: Color) {
    Spacer(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(color)
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}
