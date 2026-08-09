package id.bangkumis.dontbroke.network.api

import id.bangkumis.dontbroke.network.model.ChatRequest
import id.bangkumis.dontbroke.network.model.ChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

const val HF_TEXT_MODEL = "Qwen/Qwen2.5-7B-Instruct"

/**
 * Verified against the live router with a real receipt. The 2B/3B/7B VL models are
 * on the Hub but no router provider serves them — they answer HTTP 400 "not
 * supported by any provider you have enabled", which is indistinguishable from a
 * network failure at the call site. Check `GET /v1/models` before changing this.
 */
const val HF_VISION_MODEL = "Qwen/Qwen3-VL-30B-A3B-Instruct"

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