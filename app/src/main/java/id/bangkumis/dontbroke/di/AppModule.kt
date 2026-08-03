package id.bangkumis.dontbroke.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.bangkumis.dontbroke.BuildConfig
import id.bangkumis.dontbroke.data.database.AppDatabase
import id.bangkumis.dontbroke.data.local.dao.AccountDao
import id.bangkumis.dontbroke.data.local.dao.TransactionDao
import id.bangkumis.dontbroke.data.preferences.UserPreferencesRepository
import id.bangkumis.dontbroke.network.api.GeminiApi
import id.bangkumis.dontbroke.security.AppLockManager
import id.bangkumis.dontbroke.security.BiometricPromptManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "dontbroke.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides @Singleton
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides @Singleton
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides @Singleton
    fun provideUserPreferences(@ApplicationContext ctx: Context) = UserPreferencesRepository(ctx)

    @Provides @Singleton
    fun provideBiometricPromptManager(@ApplicationContext ctx: Context) = BiometricPromptManager(ctx)

    /**
     * Main.immediate so lock state flips before the next frame — a dispatch hop
     * here is a frame of visible balances on the way back from background.
     */
    @Provides @Singleton
    fun provideAppLockManager(prefs: UserPreferencesRepository) = AppLockManager(
        isEnabled = prefs.isBiometricEnabled,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    @Provides @Singleton
    fun provideGeminiApi(client: OkHttpClient): GeminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GeminiApi::class.java)
}
