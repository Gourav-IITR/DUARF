package com.duarf.app.ui

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.duarf.app.ui.screens.*

@Composable
fun DuarfNavGraph(
    navController: NavHostController,
    viewModel: DuarfViewModel,
    startDestination: String
) {
    val uiState by viewModel.uiState.collectAsState()
    val manualResult by viewModel.manualCheckResult.collectAsState()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onFinished = {
                    viewModel.setOnboardingCompleted()
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
                onSendTestAlert = {
                    viewModel.sendTestAlert()
                }
            )
        }

        composable("home") {
            HomeScreen(
                uiState = uiState,
                onNavigateSettings = { navController.navigate("settings") },
                onNavigateHistory = { navController.navigate("history") },
                onNavigatePrivacyProof = { navController.navigate("privacy_proof") },
                onAlertClick = { alertId -> navController.navigate("alert_detail/$alertId") },
                onCheckMessage = { text, isUnknown ->
                    viewModel.analyzeMessage(text, isUnknown)
                    navController.navigate("check_result")
                }
            )
        }

        composable(
            route = "alert_detail/{alertId}",
            arguments = listOf(navArgument("alertId") { type = NavType.LongType })
        ) { backStackEntry ->
            val alertId = backStackEntry.arguments?.getLong("alertId") ?: 0L
            val alert = uiState.recentAlerts.firstOrNull { it.id == alertId }
            if (alert != null) {
                AlertDetailScreen(
                    alert = alert,
                    onBack = { navController.popBackStack() },
                    onFeedback = { isScam -> viewModel.setAlertFeedback(alertId, isScam) },
                    onTrustSender = {
                        // Trust sender
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("check_result") {
            if (manualResult != null) {
                val (text, verdict) = manualResult!!
                CheckResultScreen(
                    messageText = text,
                    verdict = verdict,
                    onBack = {
                        viewModel.clearManualCheck()
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("history") {
            val allAlerts by viewModel.allAlerts.collectAsState(initial = emptyList())
            HistoryScreen(
                alerts = allAlerts,
                onBack = { navController.popBackStack() },
                onAlertClick = { alertId -> navController.navigate("alert_detail/$alertId") },
                onDismissAlert = { alertId -> viewModel.dismissAlert(alertId) }
            )
        }

        composable("settings") {
            SettingsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onUpdateSensitivity = { viewModel.updateSensitivity(it) },
                onUpdateLanguage = { viewModel.updateLanguage(it) },
                onUpdateRetention = { viewModel.updateRetentionDays(it) },
                onUpdateGroupAlerts = { viewModel.updateGroupAlerts(it) },
                onNavigatePrivacyProof = { navController.navigate("privacy_proof") },
                onNavigateAbout = { navController.navigate("about") },
                onDeleteAllData = { viewModel.deleteAllData() }
            )
        }

        composable("privacy_proof") {
            PrivacyProofScreen(onBack = { navController.popBackStack() })
        }

        composable("about") {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
