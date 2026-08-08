package id.bangkumis.dontbroke.network.model

import com.google.gson.annotations.SerializedName

/**
 * OpenAI-shaped chat completion body, which is what Hugging Face serves at
 * `models/{modelId}/v1/chat/completions`.
 *
 * [ChatMessage.content] is `Any` because the same wire field is either a plain
 * string (text models) or a list of `{type, text}` / `{type, image_url}` parts
 * (vision models). Gson serialises the runtime type, so both work without a
 * second request class.
 */
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 256,
)

data class ChatMessage(val role: String, val content: Any)

data class ChatResponse(val choices: List<Choice>?) {
    data class Choice(val message: Message?)
    data class Message(val content: String?)

    fun text(): String =
        choices?.firstOrNull()?.message?.content?.trim().orEmpty()
            .ifEmpty { "No insight available." }
}
