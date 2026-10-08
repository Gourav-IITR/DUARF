package com.duarf.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.capture.notification.WaNotificationListener

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onSendTestAlert: () -> Unit,
    selectedLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onNavigateBatteryGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    // Saveable: choosing a language recreates the activity, and the user should stay on that step.
    var currentStep by rememberSaveable { mutableStateOf(0) }
    val lastStep = 6
    var hasPostNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotificationPermission = granted
        if (granted && currentStep < lastStep) currentStep++
    }

    val isListenerEnabled = remember(currentStep) {
        WaNotificationListener.isNotificationServiceEnabled(context)
    }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page indicator dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0..lastStep) {
                        Box(
                            modifier = Modifier
                                .size(if (i == currentStep) 10.dp else 8.dp)
                                .background(
                                    if (i == currentStep) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                    }
                }

                if (currentStep < lastStep) {
                    Button(onClick = { currentStep++ }) {
                        Text(stringResource(R.string.btn_next))
                    }
                } else {
                    Button(onClick = onFinished) {
                        Text(stringResource(R.string.btn_get_started))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            when (currentStep) {
                // Step 0: language first, so every later step is read in it (English unless changed).
                0 -> LanguageChooser(
                    selectedTag = selectedLanguage,
                    onSelect = onSelectLanguage,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp)
                )
                1 -> StepContent(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.onboarding_title_1),
                    desc = stringResource(R.string.onboarding_desc_1)
                )
                2 -> StepContent(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.onboarding_title_2),
                    desc = stringResource(R.string.onboarding_desc_2)
                )
                3 -> StepContent(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.onboarding_title_3),
                    desc = stringResource(R.string.onboarding_desc_3),
                    actionButton = {
                        if (!isListenerEnabled) {
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }) {
                                Text(stringResource(R.string.btn_grant_notification_access))
                            }
                        } else {
                            Text(
                                text = "✓ " + stringResource(R.string.access_granted),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
                4 -> StepContent(
                    icon = Icons.Default.NotificationAdd,
                    title = stringResource(R.string.onboarding_title_4),
                    desc = stringResource(R.string.onboarding_desc_4),
                    actionButton = {
                        if (!hasPostNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(onClick = {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) {
                                Text(stringResource(R.string.btn_grant_post_notifications))
                            }
                        } else {
                            Text(
                                text = "✓ " + stringResource(R.string.access_granted),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
                5 -> StepContent(
                    icon = Icons.Default.BatteryChargingFull,
                    title = stringResource(R.string.onboarding_title_5),
                    desc = stringResource(R.string.onboarding_desc_5),
                    actionButton = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = onNavigateBatteryGuide) {
                                Text(stringResource(R.string.btn_view_device_guide))
                            }
                            OutlinedButton(onClick = {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }) {
                                Text(stringResource(R.string.btn_battery_settings))
                            }
                        }
                    }
                )
                6 -> StepContent(
                    icon = Icons.Default.VerifiedUser,
                    title = stringResource(R.string.onboarding_title_6),
                    desc = stringResource(R.string.onboarding_desc_6),
                    actionButton = {
                        Button(onClick = onSendTestAlert) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_send_test_alert))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StepContent(
    icon: ImageVector,
    title: String,
    desc: String,
    actionButton: (@Composable () -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionButton != null) {
            Spacer(Modifier.height(28.dp))
            actionButton()
        }
    }
}
