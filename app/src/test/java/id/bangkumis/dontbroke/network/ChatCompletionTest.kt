package id.bangkumis.dontbroke.network

import com.google.gson.Gson
import id.bangkumis.dontbroke.network.api.HF_TEXT_MODEL
import id.bangkumis.dontbroke.network.api.HF_VISION_MODEL
import id.bangkumis.dontbroke.network.model.ChatMessage
import id.bangkumis.dontbroke.network.model.ChatRequest
import id.bangkumis.dontbroke.network.model.ChatResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wire contract with Hugging Face's OpenAI-compatible endpoint. */
class ChatCompletionTest {

    private val gson = Gson()

    @Test fun readsContentFromFirstChoice() {
        val json = """
            {"choices":[{"index":0,"message":{"role":"assistant","content":" Hemat sedikit. "}}]}
        """.trimIndent()
        assertEquals("Hemat sedikit.", gson.fromJson(json, ChatResponse::class.java).text())
    }

    @Test fun emptyOrMalformedResponseFallsBack() {
        listOf("""{"choices":[]}""", """{}""", """{"choices":[{"message":{}}]}""").forEach {
            assertEquals("No insight available.", gson.fromJson(it, ChatResponse::class.java).text())
        }
    }

    /** Gson keeps camelCase by default, so max_tokens needs its @SerializedName. */
    @Test fun requestUsesSnakeCaseMaxTokens() {
        val body = gson.toJson(ChatRequest(HF_TEXT_MODEL, listOf(ChatMessage("user", "hi"))))
        assertTrue(body, body.contains("\"max_tokens\""))
        assertTrue(body, body.contains("\"content\":\"hi\""))
    }

    /** The router takes the model in the body — the old path segment 404s. */
    @Test fun requestCarriesModelInBody() {
        val body = gson.toJson(ChatRequest(HF_TEXT_MODEL, listOf(ChatMessage("user", "hi"))))
        assertTrue(body, body.contains("\"model\":\"Qwen/Qwen2.5-7B-Instruct\""))
    }

    /** Vision models take the same field as a list of parts. */
    @Test fun contentAlsoSerialisesAsParts() {
        val parts = listOf(mapOf("type" to "text", "text" to "what is this?"))
        val body = gson.toJson(ChatRequest(HF_VISION_MODEL, listOf(ChatMessage("user", parts))))
        assertTrue(body, body.contains("\"content\":[{"))
    }
}
