package id.bangkumis.dontbroke.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unconfined so `initialize` mirrors the setting inside the call — the fake flow
 * emits immediately, matching the activity, which always initializes before any
 * lifecycle callback can arrive.
 */
private fun manager(enabled: Boolean) =
    AppLockManager(flowOf(enabled), CoroutineScope(Dispatchers.Unconfined)).also { it.initialize() }

class AppLockManagerTest {

    @Test fun coldStartLocksWhenEnabled() {
        assertTrue(manager(enabled = true).isLocked.value)
    }

    @Test fun coldStartStaysUnlockedWhenDisabled() {
        assertFalse(manager(enabled = false).isLocked.value)
    }

    @Test fun foregroundWithoutBackgroundDoesNotLock() {
        val lock = manager(enabled = true)
        lock.unlock()
        lock.onForeground()
        assertFalse(lock.isLocked.value)
    }

    @Test fun backgroundThenForegroundLocks() {
        val lock = manager(enabled = true)
        lock.unlock()
        lock.onBackground()
        lock.onForeground()
        assertTrue(lock.isLocked.value)
    }

    @Test fun backgroundThenForegroundDoesNothingWhenDisabled() {
        val lock = manager(enabled = false)
        lock.onBackground()
        lock.onForeground()
        assertFalse(lock.isLocked.value)
    }

    /** The lock is armed once, not permanently: a second foreground must not re-lock. */
    @Test fun lockDoesNotRearmAfterUnlock() {
        val lock = manager(enabled = true)
        lock.onBackground()
        lock.onForeground()
        lock.unlock()
        lock.onForeground()
        assertFalse(lock.isLocked.value)
    }

    /** The API 30+ credential prompt backgrounds the app; that must not arm the lock. */
    @Test fun backgroundWhilePromptingDoesNotArm() {
        val lock = manager(enabled = true)
        assertTrue(lock.beginPrompt())
        lock.onBackground()
        lock.unlock()
        lock.onForeground()
        assertFalse(lock.isLocked.value)
    }

    @Test fun secondPromptIsRefusedWhileOneIsUp() {
        val lock = manager(enabled = true)
        assertTrue(lock.beginPrompt())
        assertFalse(lock.beginPrompt())
        lock.endPrompt()
        assertTrue(lock.beginPrompt())
    }

    @Test fun disablingClearsArmedLock() {
        val lock = manager(enabled = true)
        lock.onBackground()
        lock.onLockDisabled()
        assertFalse(lock.isLocked.value)
        lock.onForeground()
        assertFalse(lock.isLocked.value)
    }

    /**
     * The regression that motivated mirroring the setting into a field: a quick
     * app-switch delivers ON_STOP and ON_START back to back, and an `onBackground`
     * that started its own DataStore read would arm the lock only after the
     * foreground callback had passed — leaving balances on screen.
     *
     * The fake answers the first collection and stalls every later one, which is
     * what a read issued at ON_STOP looks like when the user is already coming back.
     */
    @Test fun fastAppSwitchStillLocks() {
        var collections = 0
        val prefs = flow {
            if (collections++ == 0) emit(true) else awaitCancellation()
        }
        val lock = AppLockManager(prefs, CoroutineScope(Dispatchers.Unconfined))
        lock.initialize()
        lock.unlock()
        lock.onBackground()
        lock.onForeground()
        assertTrue(lock.isLocked.value)
    }
}
