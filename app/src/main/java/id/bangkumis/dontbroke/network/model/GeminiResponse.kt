package id.bangkumis.dontbroke.network.model

data class GeminiResponse(val candidates: List<Candidate>?) {
    data class Candidate(val content: Content?)
    data class Content(val parts: List<Part>?)
    data class Part(val text: String?)

    fun text(): String = candidates
        ?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        ?: "No insight available."
}
