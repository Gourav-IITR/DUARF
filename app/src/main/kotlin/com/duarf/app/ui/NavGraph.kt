package com.duarf.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.duarf.app.locale.AppLanguage
import com.duarf.app.ui.screens.*

@Composable
fun DuarfNavGraph(
    navController: NavHostController,
    viewModel: DuarfViewModel,
    startDestination: String
) {
    val uiState by viewModel.uiState.collectAsState()
    val manualResult by viewModel.manualCheckResult.collectAsState()
    val familyContact by viewModel.familyContact.collectAsState()

    // UI language only. Applying it recreates the activity, so this is re-read afterwards.
    val context = LocalContext.current
    val uiLanguage = remember { AppLanguage.current(context) }
    val selectLanguage: (String) -> Unit = { tag ->
        if (tag != uiLanguage) {
            viewModel.updateLanguage(tag)
            AppLanguage.set(context, tag)
        }
    }

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
                },
                selectedLanguage = uiLanguage,
                onSelectLanguage = selectLanguage,
                familyContact = familyContact,
                onSaveFamilyContact = viewModel::saveFamilyContact,
                onRemoveFamilyContact = viewModel::removeFamilyContact,
                onNavigateBatteryGuide = {
                    navController.navigate("battery_guide")
                }
            )
        }

        composable("home") {
            HomeScreen(
                uiState = uiState,
                onNavigateSettings = { navController.navigate("settings") },
                onNavigateHistory = { navController.navigate("history") },
                onNavigatePrivacyProof = { navController.navigate("privacy_proof") },
                onNavigateLanguage = { navController.navigate("language") },
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
                    familyContact = familyContact,
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
                    familyContact = familyContact,
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
                onNavigateLanguage = { navController.navigate("language") },
                familyContact = familyContact,
                onNavigateFamily = { navController.navigate("family") },
                onUpdateRetention = { viewModel.updateRetentionDays(it) },
                onUpdateCheckSms = { viewModel.updateCheckSms(it) },
                onUpdateGroupAlerts = { viewModel.updateGroupAlerts(it) },
                onNavigatePrivacyProof = { navController.navigate("privacy_proof") },
                onNavigateBatteryGuide = { navController.navigate("battery_guide") },
                onNavigateAbout = { navController.navigate("about") },
                onDeleteAllData = { viewModel.deleteAllData() }
            )
        }

        // Danger warnings open here first: one screen, no scrolling needed.
        composable(
            route = "stop/{alertId}",
            arguments = listOf(navArgument("alertId") { type = NavType.LongType })
        ) { backStackEntry ->
            val alertId = backStackEntry.arguments?.getLong("alertId") ?: 0L
            val alert = uiState.recentAlerts.firstOrNull { it.id == alertId }
            if (alert != null) {
                StopScreen(
                    alert = alert,
                    familyContact = familyContact,
                    onDone = { navController.popBackStack() },
                    onDetails = {
                        navController.navigate("alert_detail/$alertId") {
                            popUpTo("stop/{alertId}") { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("family") {
            FamilyContactScreen(
                contact = familyContact,
                onSave = viewModel::saveFamilyContact,
                onRemove = viewModel::removeFamilyContact,
                onBack = { navController.popBackStack() }
            )
        }

        composable("language") {
            LanguageScreen(
                selectedTag = uiLanguage,
                onSelect = selectLanguage,
                onBack = { navController.popBackStack() }
            )
        }

        composable("battery_guide") {
            BatteryOptimizationScreen(onBack = { navController.popBackStack() })
        }

        composable("privacy_proof") {
            PrivacyProofScreen(onBack = { navController.popBackStack() })
        }

        composable("about") {
            AboutScreen(
                isModelLoaded = viewModel.isModelLoaded,
                modelVersion = viewModel.modelVersion,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
