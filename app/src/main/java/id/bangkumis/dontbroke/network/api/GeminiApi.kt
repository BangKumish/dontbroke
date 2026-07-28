package id.bangkumis.dontbroke.network.api

import id.bangkumis.dontbroke.BuildConfig
import id.bangkumis.dontbroke.network.model.GeminiRequest
import id.bangkumis.dontbroke.network.model.GeminiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApi {
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String = BuildConfig.GEMINI_API_KEY,
        @Body request: GeminiRequest
    ): GeminiResponse
}
