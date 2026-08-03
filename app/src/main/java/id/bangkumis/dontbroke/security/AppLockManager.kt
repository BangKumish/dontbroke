package id.bangkumis.dontbroke.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether the balance UI is currently hidden behind the unlock overlay.
 *
 * Process-scoped rather than ViewModel-scoped: the lock has to survive activity
 * recreation (rotation, theme change) or turning the screen would reveal the
 * balances it exists to cover.
 *
 * Takes the preference as a plain [Flow] rather than the repository so the whole
 * class stays JVM-testable — see `AppLockManagerTest`.
 */
class AppLockManager(
    private val isEnabled: Flow<Boolean>,
    private val scope: CoroutineScope
) {

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    /**
     * True once the app has gone to background with the lock enabled. Kept
     * separate from [isLocked] so the very first foreground of a cold start does
     * not prompt twice — see [onForeground].
     */
    private var lockOnReturn = false

    /** True while a prompt is on screen, so re-entry does not stack a second one. */
    private var prompting = false

    /**
     * The setting mirrored into a plain field. [onBackground] has to decide
     * synchronously: awaiting a DataStore read there loses a fast app-switch, where
     * the foreground callback lands before the read resolves and the lock never arms.
     */
    private var enabled = false

    /**
     * Starts mirroring the setting, and locks a cold start when it is already on.
     * Called once from the activity; the first DataStore read suspends, so the UI
     * is briefly unlocked — the overlay covers it as soon as this resolves.
     */
    fun initialize() = scope.launch {
        var coldStart = true
        isEnabled.collect {
            enabled = it
            if (coldStart) {
                coldStart = false
                if (it) _isLocked.value = true
            }
        }
    }

    /**
     * App backgrounded: arm the lock, but only if the user asked for one.
     *
     * Inert while a prompt is in flight. On API 30+ the PIN/pattern fallback runs
     * in the system's own activity, which backgrounds ours — without this guard a
     * successful credential unlock would re-arm the lock and prompt again forever.
     */
    fun onBackground() {
        if (!prompting && enabled) lockOnReturn = true
    }

    /**
     * App foregrounded. Locks only when [onBackground] armed it, so this is inert
     * on the first start and after an in-app configuration change.
     */
    fun onForeground() {
        if (lockOnReturn) {
            lockOnReturn = false
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
        prompting = false
    }

    /** Marks a prompt in flight. False means one is already showing — do not prompt again. */
    fun beginPrompt(): Boolean {
        if (prompting) return false
        prompting = true
        return true
    }

    fun endPrompt() {
        prompting = false
    }

    /**
     * Turning the setting off must clear an armed lock too, or the next foreground
     * prompts for a lock the user just disabled.
     */
    fun onLockDisabled() {
        lockOnReturn = false
        prompting = false
        _isLocked.value = false
    }
}
