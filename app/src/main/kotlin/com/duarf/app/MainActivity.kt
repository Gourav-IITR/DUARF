package com.duarf.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.duarf.app.notification.NotificationDispatcher
import com.duarf.app.ui.DuarfNavGraph
import com.duarf.app.ui.DuarfViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DuarfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val uiState by viewModel.uiState.collectAsState()

                    val startDestination = if (uiState.preferences.onboardingCompleted) "home" else "onboarding"

                    DuarfNavGraph(
                        navController = navController,
                        viewModel = viewModel,
                        startDestination = startDestination
                    )

                    // Handle intents (deep links from notifications or share sheet)
                    handleIntent(intent, navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?, navController: androidx.navigation.NavHostController) {
        if (intent == null) return

        // Deep link from notification tap (§13.2)
        val alertId = intent.getLongExtra(NotificationDispatcher.EXTRA_NAV_ALERT_ID, -1L)
        if (alertId != -1L) {
            navController.navigate("alert_detail/$alertId")
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
