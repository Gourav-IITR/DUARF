// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.duarf.app.locale.AppLanguage
import com.duarf.app.notification.NotificationDispatcher
import com.duarf.app.ui.DuarfNavGraph
import com.duarf.app.ui.DuarfViewModel
import com.duarf.app.ui.theme.DuarfTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DuarfViewModel by viewModels()

    /** A notification tap or shared message still to be acted on; consumed once. */
    private val pendingIntent = mutableStateOf<Intent?>(null)

    // Android 8–12 have no per-app language; apply the chosen one here (no-op on 13+).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is light-themed, so system bar icons stay dark even when the phone is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        // A recreated activity (language change, rotation) must not replay the launch intent.
        if (savedInstanceState == null) pendingIntent.value = intent

        setContent {
            DuarfTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val uiState by viewModel.uiState.collectAsState()
                    if (uiState.loaded) {
                        val navController = rememberNavController()
                        val startDestination = if (uiState.preferences.onboardingCompleted) "home" else "onboarding"

                        DuarfNavGraph(
                            navController = navController,
                            viewModel = viewModel,
                            startDestination = startDestination
                        )

                        val pending by pendingIntent
                        LaunchedEffect(pending) {
                            pending?.let {
                                handleIntent(it, navController)
                                pendingIntent.value = null
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshListenerHealth(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }

    private fun handleIntent(intent: Intent, navController: NavHostController) {
        // Deep link from a notification (§13.2): Danger opens the Stop screen, Caution the full detail.
        val alertId = intent.getLongExtra(NotificationDispatcher.EXTRA_NAV_ALERT_ID, -1L)
        if (alertId != -1L) {
            val toStop = intent.getBooleanExtra(NotificationDispatcher.EXTRA_NAV_STOP, false)
            navController.navigate(if (toStop) "stop/$alertId" else "alert_detail/$alertId")
            return
        }

        // Shared text/file from CheckMessageActivity (§5.3)
        val checkText = intent.getStringExtra("EXTRA_CHECK_TEXT")
        val attachmentHint = intent.getStringExtra("EXTRA_ATTACHMENT_HINT")
        if (!checkText.isNullOrBlank()) {
            viewModel.analyzeMessage(checkText, isUnknownNumber = true, attachmentHint = attachmentHint)
            navController.navigate("check_result")
        }
    }
}
