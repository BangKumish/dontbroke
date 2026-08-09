package id.bangkumis.dontbroke.network.model

import com.google.gson.annotations.SerializedName

/**
 * OpenAI-shaped chat completion body, which is what Hugging Face serves at
 * `v1/chat/completions`.
 *
 * [ChatMessage.content] is `Any` because the same wire field is either a plain
 * string (text models) or a list of [ContentPart] (vision models). Gson
 * serialises the runtime type, so both work without a second request class.
 */
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 256,
)

data class ChatMessage(val role: String, val content: Any)

/**
 * A single piece of a multimodal user turn. `type` is a real wire field, not a
 * Kotlin discriminator, so it is declared with a default rather than derived.
 */
sealed interface ContentPart

data class TextPart(
    val text: String,
    val type: String = "text",
) : ContentPart

data class ImagePart(
    @SerializedName("image_url") val imageUrl: ImageUrl,
    val type: String = "image_url",
) : ContentPart

/** `url` carries a `data:image/jpeg;base64,…` payload, not an http address. */
data class ImageUrl(val url: String)

data class ChatResponse(val choices: List<Choice>?) {
    data class Choice(val message: Message?)
    data class Message(val content: String?)

    /** Raw model text, or blank — callers that parse JSON need to see "blank". */
    fun content(): String = choices?.firstOrNull()?.message?.content?.trim().orEmpty()

    fun text(): String = content().ifEmpty { "No insight available." }
}
