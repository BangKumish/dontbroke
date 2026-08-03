package id.bangkumis.dontbroke.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One DataStore per process, owned by the extension property rather than by DI —
 * creating two instances over the same file throws at runtime.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** User-facing settings that outlive the process. Currently only the app lock. */
@Singleton
class UserPreferencesRepository @Inject constructor(context: Context) {

    private val store = context.dataStore

    /**
     * Off unless explicitly enabled. A missing key must read as "no lock" or a
     * fresh install would strand the user behind a prompt they never set up.
     */
    val isBiometricEnabled: Flow<Boolean> =
        store.data.map { it[IS_BIOMETRIC_ENABLED] ?: false }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        store.edit { it[IS_BIOMETRIC_ENABLED] = enabled }
    }

    private companion object {
        val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
    }
}
