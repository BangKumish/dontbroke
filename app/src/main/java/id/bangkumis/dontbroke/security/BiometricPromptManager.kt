package id.bangkumis.dontbroke.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the device can prompt at all, and if not, why — the UI explains the reason. */
enum class BiometricAvailability { Ready, HardwareUnavailable, NoEnrolledBiometrics, FeatureUnavailable }

/** Outcome of one prompt. [AuthenticationFailed] is a rejected finger, not a closed prompt. */
sealed interface BiometricResult {
    data object Success : BiometricResult
    data object AuthenticationFailed : BiometricResult
    data class Error(val message: String) : BiometricResult
    data object Cancelled : BiometricResult
}

/**
 * Wraps [BiometricManager] / [BiometricPrompt] behind domain types so callers never
 * touch framework constants.
 *
 * Biometric *or* device PIN/pattern is accepted: an app lock that outlives a
 * re-enrolled fingerprint is worth more here than crypto-grade binding, since this
 * gate guards a local balance view and unlocks no keys.
 */
@Singleton
class BiometricPromptManager @Inject constructor(private val context: Context) {

    /**
     * DEVICE_CREDENTIAL alongside a biometric class is only permitted from API 30.
     * Below that the constant combination throws, so weak biometrics stand alone
     * and API 28-29's own credential fallback is left to the prompt itself.
     *
     * canAuthenticate() and the prompt MUST use this same value — asking about one
     * set and prompting with another is what produces the runtime IllegalArgument.
     */
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK
        }

    fun canAuthenticate(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Ready
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoEnrolledBiometrics
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.HardwareUnavailable
            else -> BiometricAvailability.FeatureUnavailable
        }

    /**
     * Shows the prompt and reports once through [onResult].
     *
     * [onResult] can fire more than once in the framework's own callbacks — a
     * rejected finger calls onAuthenticationFailed and leaves the prompt open — so
     * only the terminal callbacks close the flow. Callers treat
     * [BiometricResult.AuthenticationFailed] as "still locked, still prompting".
     */
    fun showBiometricPrompt(activity: FragmentActivity, onResult: (BiometricResult) -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Dont Broke")
            .setSubtitle("Confirm it's you to view your balances")
            .setAllowedAuthenticators(authenticators)
            .apply {
                // A negative button is required exactly when no device credential is
                // offered; setting both is rejected by the builder.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText("Cancel")
            }
            .build()

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onResult(BiometricResult.Success)

                override fun onAuthenticationFailed() = onResult(BiometricResult.AuthenticationFailed)

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    val cancelled = code == BiometricPrompt.ERROR_USER_CANCELED ||
                        code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        code == BiometricPrompt.ERROR_CANCELED
                    onResult(
                        if (cancelled) BiometricResult.Cancelled
                        else BiometricResult.Error(message.toString())
                    )
                }
            }
        )
        prompt.authenticate(info)
    }
}
