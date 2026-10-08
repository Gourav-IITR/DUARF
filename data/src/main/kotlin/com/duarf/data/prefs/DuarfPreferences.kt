package com.duarf.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.duarf.engine.model.Sensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "duarf_preferences")

class DuarfPreferences(private val context: Context) : UserPreferencesRepository {

    private object PreferencesKeys {
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val MONITORED_APPS = stringSetPreferencesKey("monitored_apps")
        val CHECK_SMS = booleanPreferencesKey("check_sms")
        val GROUP_ALERTS = booleanPreferencesKey("group_alerts")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val FAMILY_CONTACT_CIPHER = stringPreferencesKey("family_contact_cipher")
    }

    override val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val sensStr = prefs[PreferencesKeys.SENSITIVITY] ?: Sensitivity.BALANCED.name
        val sensitivity = try { Sensitivity.valueOf(sensStr) } catch (_: Exception) { Sensitivity.BALANCED }
        val apps = prefs[PreferencesKeys.MONITORED_APPS] ?: setOf("com.whatsapp", "com.whatsapp.w4b")
        val checkSms = prefs[PreferencesKeys.CHECK_SMS] ?: true
        val group = prefs[PreferencesKeys.GROUP_ALERTS] ?: false
        val retention = prefs[PreferencesKeys.RETENTION_DAYS] ?: 30
        val onboarded = prefs[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        val lang = prefs[PreferencesKeys.LANGUAGE_CODE] ?: "en"

        UserPreferences(
            sensitivity = sensitivity,
            monitoredApps = apps,
            checkSms = checkSms,
            groupAlerts = group,
            retentionDays = retention,
            onboardingCompleted = onboarded,
            languageCode = lang,
            familyContactCipher = prefs[PreferencesKeys.FAMILY_CONTACT_CIPHER]
        )
    }

    override suspend fun updateSensitivity(sensitivity: Sensitivity) {
        context.dataStore.edit { it[PreferencesKeys.SENSITIVITY] = sensitivity.name }
    }

    override suspend fun updateMonitoredApps(apps: Set<String>) {
        context.dataStore.edit { it[PreferencesKeys.MONITORED_APPS] = apps }
    }

    override suspend fun updateCheckSms(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.CHECK_SMS] = enabled }
    }

    override suspend fun updateGroupAlerts(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.GROUP_ALERTS] = enabled }
    }

    override suspend fun updateRetentionDays(days: Int) {
        context.dataStore.edit { it[PreferencesKeys.RETENTION_DAYS] = days }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun updateLanguageCode(langCode: String) {
        context.dataStore.edit { it[PreferencesKeys.LANGUAGE_CODE] = langCode }
    }

    override suspend fun updateFamilyContactCipher(cipher: String?) {
        context.dataStore.edit {
            if (cipher == null) it.remove(PreferencesKeys.FAMILY_CONTACT_CIPHER)
            else it[PreferencesKeys.FAMILY_CONTACT_CIPHER] = cipher
        }
    }

    override suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
