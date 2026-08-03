package id.bangkumis.dontbroke.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.bangkumis.dontbroke.data.preferences.UserPreferencesRepository
import id.bangkumis.dontbroke.security.AppLockManager
import id.bangkumis.dontbroke.security.BiometricAvailability
import id.bangkumis.dontbroke.security.BiometricPromptManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val biometrics: BiometricPromptManager,
    private val lock: AppLockManager
) : ViewModel() {

    val isBiometricEnabled: StateFlow<Boolean> =
        prefs.isBiometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)

    /** One-shot text for the snackbar; cleared by [messageShown]. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Enabling is refused unless the device can actually authenticate — storing
     * `true` against absent biometrics would lock the user out of their own data
     * with no way back in.
     */
    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch {
        if (!enabled) {
            prefs.setBiometricEnabled(false)
            lock.onLockDisabled()
            return@launch
        }
        when (val state = biometrics.canAuthenticate()) {
            BiometricAvailability.Ready -> prefs.setBiometricEnabled(true)
            else -> _message.value = state.reason
        }
    }

    fun messageShown() {
        _message.value = null
    }
}

/** User-facing explanation for why the switch refused to turn on. */
private val BiometricAvailability.reason: String
    get() = when (this) {
        BiometricAvailability.Ready -> ""
        BiometricAvailability.HardwareUnavailable ->
            "Biometric hardware is unavailable right now. Try again later."
        BiometricAvailability.NoEnrolledBiometrics ->
            "No biometrics enrolled on device. Add a fingerprint, face or screen lock in Settings first."
        BiometricAvailability.FeatureUnavailable ->
            "This device does not support biometric or screen-lock authentication."
    }
