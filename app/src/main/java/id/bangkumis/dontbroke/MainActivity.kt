package id.bangkumis.dontbroke

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import id.bangkumis.dontbroke.data.preferences.UserPreferencesRepository
import id.bangkumis.dontbroke.presentation.navigation.AppNavGraph
import id.bangkumis.dontbroke.security.AppLockManager
import id.bangkumis.dontbroke.security.BiometricPromptManager
import id.bangkumis.dontbroke.security.BiometricResult
import id.bangkumis.dontbroke.security.LockOverlay
import id.bangkumis.dontbroke.ui.theme.DontBrokeTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A [FragmentActivity] because [androidx.biometric.BiometricPrompt] requires one;
 * it extends ComponentActivity, so edge-to-edge and setContent are unaffected.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var lock: AppLockManager
    @Inject lateinit var biometrics: BiometricPromptManager
    @Inject lateinit var prefs: UserPreferencesRepository

    /**
     * Process-wide, not activity-wide: the activity's own ON_STOP also fires for a
     * configuration change and for the system credential prompt on API 30+, either
     * of which would arm the lock spuriously.
     */
    private val appLifecycle = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { lock.onBackground() }
        override fun onStart(owner: LifecycleOwner) { lock.onForeground() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lock.initialize()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycle)

        // FLAG_SECURE tracks the setting instead of being set once, so disabling the
        // lock restores screenshots without a restart. It must be on the window
        // before the task snapshot is taken, which is why it is not tied to isLocked.
        lifecycleScope.launch {
            prefs.isBiometricEnabled.distinctUntilChanged().collect { enabled ->
                if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContent {
            DontBrokeTheme {
                val locked by lock.isLocked.collectAsState()
                // Composed only while unlocked: the nav graph's ViewModels — and the
                // balances they hold — never reach the composition behind the overlay.
                if (locked) LockOverlay(onUnlock = ::authenticate) else AppNavGraph()

                LaunchedEffect(locked) { if (locked) authenticate() }
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycle)
        super.onDestroy()
    }

    /** No-ops when a prompt is already up, so overlay taps cannot stack prompts. */
    private fun authenticate() {
        if (!lock.beginPrompt()) return
        biometrics.showBiometricPrompt(this) { result ->
            when (result) {
                BiometricResult.Success -> lock.unlock()
                // A rejected finger leaves the prompt open — the framework retries
                // on its own, so holding `prompting` here is correct.
                BiometricResult.AuthenticationFailed -> Unit
                // Cancel and error both close the prompt and leave the overlay up;
                // releasing the latch is what makes the Unlock button work again.
                is BiometricResult.Error, BiometricResult.Cancelled -> lock.endPrompt()
            }
        }
    }
}
