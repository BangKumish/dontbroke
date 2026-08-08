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
import id.bangkumis.dontbroke.network.api.HuggingFaceApi
import id.bangkumis.dontbroke.security.AppLockManager
import id.bangkumis.dontbroke.security.BiometricPromptManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
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

    /**
     * The key rides on a per-API interceptor rather than the shared client, so a
     * future Retrofit built on the same OkHttp does not leak it to another host.
     * Read timeout is generous because a cold serverless model can sit in
     * "loading" for well past OkHttp's 10s default.
     */
    @Provides @Singleton
    fun provideHuggingFaceApi(client: OkHttpClient): HuggingFaceApi = Retrofit.Builder()
        .baseUrl("https://router.huggingface.co/")
        .client(
            client.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer ${BuildConfig.HF_API_KEY}")
                            .build()
                    )
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(HuggingFaceApi::class.java)
}
