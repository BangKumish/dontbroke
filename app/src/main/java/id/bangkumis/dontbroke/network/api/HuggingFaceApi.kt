package id.bangkumis.dontbroke.network.api

import id.bangkumis.dontbroke.network.model.ChatRequest
import id.bangkumis.dontbroke.network.model.ChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

const val HF_TEXT_MODEL = "Qwen/Qwen2.5-7B-Instruct"
const val HF_VISION_MODEL = "Qwen/Qwen2-VL-2B-Instruct"

/**
 * Hugging Face's OpenAI-compatible router. The model is a body field, not a path
 * segment: `api-inference.huggingface.co` and the old
 * `models/{modelId}/v1/chat/completions` form are both retired — the host no
 * longer resolves in DNS, and that path 404s on the router.
 */
interface HuggingFaceApi {
    @POST("v1/chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}