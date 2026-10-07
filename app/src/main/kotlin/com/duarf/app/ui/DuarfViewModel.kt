package com.duarf.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duarf.app.service.MessageCoordinator
import com.duarf.capture.notification.ListenerHealth
import com.duarf.capture.notification.WaNotificationListener
import com.duarf.data.db.DailyCounterEntity
import com.duarf.data.prefs.DuarfPreferences
import com.duarf.data.prefs.UserPreferences
import com.duarf.data.repo.AlertRepository
import com.duarf.data.repo.DecryptedAlert
import com.duarf.data.repo.StatsRepository
import com.duarf.engine.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiState(
    val listenerHealth: ListenerHealth = ListenerHealth(),
    val preferences: UserPreferences = UserPreferences(),
    val weeklyStats: List<DailyCounterEntity> = emptyList(),
    val recentAlerts: List<DecryptedAlert> = emptyList(),
    val manualCheckVerdict: Verdict? = null,
    val manualCheckText: String? = null,
    val isCheckingMessage: Boolean = false
)

@HiltViewModel
class DuarfViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val statsRepository: StatsRepository,
    private val preferences: DuarfPreferences,
    private val scamEngine: ScamEngine,
    private val messageCoordinator: MessageCoordinator
) : ViewModel() {

    private val _manualCheckResult = MutableStateFlow<Pair<String, Verdict>?>(null)
    val manualCheckResult: StateFlow<Pair<String, Verdict>?> = _manualCheckResult.asStateFlow()

    val isModelLoaded: Boolean get() = scamEngine.isModelLoaded
    val modelVersion: Int? get() = scamEngine.modelVersion

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    val uiState: StateFlow<UiState> = combine(
        WaNotificationListener.healthState,
        preferences.userPreferencesFlow,
        statsRepository.weeklyCounters,
        alertRepository.activeAlerts
    ) { health, prefs, stats, alerts ->
        UiState(
            listenerHealth = health,
            preferences = prefs,
            weeklyStats = stats,
            recentAlerts = alerts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    val allAlerts: Flow<List<DecryptedAlert>> = alertRepository.allAlerts

    fun analyzeMessage(text: String, isUnknownNumber: Boolean = true, attachmentHint: String? = null) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isChecking.value = true
            try {
                val prefs = preferences.userPreferencesFlow.first()
                val msg = IncomingMessage(
                    fingerprint = "manual-${System.currentTimeMillis()}",
                    source = SourceKind.PASTE,
                    app = SourceApp.UNKNOWN,
                    conversationKey = null,
                    senderDisplay = null,
                    senderKind = if (isUnknownNumber) SenderKind.NUMBER_ONLY else SenderKind.UNKNOWN,
                    senderCountryCode = "+91",
                    isGroup = false,
                    text = text,
                    attachmentHint = attachmentHint,
                    receivedAtMillis = System.currentTimeMillis()
                )
                val verdict = scamEngine.analyze(msg, emptyList(), prefs.sensitivity)
                _manualCheckResult.value = Pair(text, verdict)
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun clearManualCheck() {
        _manualCheckResult.value = null
    }

    fun setOnboardingCompleted() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
        }
    }

    fun refreshListenerHealth(context: android.content.Context) {
        WaNotificationListener.refreshHealth(context)
    }

    fun updateSensitivity(sensitivity: Sensitivity) {
        viewModelScope.launch {
            preferences.updateSensitivity(sensitivity)
        }
    }

    fun updateLanguage(langCode: String) {
        viewModelScope.launch {
            preferences.updateLanguageCode(langCode)
        }
    }

    fun updateRetentionDays(days: Int) {
        viewModelScope.launch {
            preferences.updateRetentionDays(days)
        }
    }

    fun updateCheckSms(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateCheckSms(enabled)
        }
    }

    fun updateGroupAlerts(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateGroupAlerts(enabled)
        }
    }

    fun setAlertFeedback(alertId: Long, isScam: Boolean) {
        viewModelScope.launch {
            alertRepository.updateFeedback(alertId, if (isScam) "SCAM" else "SAFE")
        }
    }

    fun dismissAlert(alertId: Long) {
        viewModelScope.launch {
            alertRepository.dismissAlert(alertId)
        }
    }

    fun trustSender(conversationKey: String?) {
        if (conversationKey == null) return
        viewModelScope.launch {
            statsRepository.setConversationTrusted(conversationKey, true)
        }
    }

    fun sendTestAlert() {
        viewModelScope.launch {
            val testMsg = IncomingMessage(
                fingerprint = "test-alert-${System.currentTimeMillis()}",
                source = SourceKind.NOTIFICATION,
                app = SourceApp.WHATSAPP,
                conversationKey = "test-conv",
                senderDisplay = "+919876543210",
                senderKind = SenderKind.NUMBER_ONLY,
                senderCountryCode = "+91",
                isGroup = false,
                text = "SBI Alert: Your account is blocked within 24 hours. Update KYC at http://sbi-kyc-verify.xyz immediately.",
                attachmentHint = null,
                receivedAtMillis = System.currentTimeMillis()
            )
            messageCoordinator.processMessage(testMsg)
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            alertRepository.deleteAllData()
            statsRepository.deleteAll()
        }
    }
}
