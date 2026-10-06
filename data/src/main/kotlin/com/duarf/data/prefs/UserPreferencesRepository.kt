package com.duarf.data.prefs

import com.duarf.engine.model.Sensitivity
import kotlinx.coroutines.flow.Flow

data class UserPreferences(
    val sensitivity: Sensitivity = Sensitivity.BALANCED,
    val monitoredApps: Set<String> = setOf("com.whatsapp", "com.whatsapp.w4b"),
    val checkSms: Boolean = true,
    val groupAlerts: Boolean = false,
    val retentionDays: Int = 30,
    val onboardingCompleted: Boolean = false,
    val languageCode: String = "en"
)

interface UserPreferencesRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun updateSensitivity(sensitivity: Sensitivity)
    suspend fun updateMonitoredApps(apps: Set<String>)
    suspend fun updateCheckSms(enabled: Boolean)
    suspend fun updateGroupAlerts(enabled: Boolean)
    suspend fun updateRetentionDays(days: Int)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun updateLanguageCode(langCode: String)
    suspend fun clearAll()
}
