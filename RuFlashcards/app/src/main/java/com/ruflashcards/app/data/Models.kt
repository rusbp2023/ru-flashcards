package com.ruflashcards.app.data

data class WordItem(
    val id: Long,
    val original: String
)

data class Flashcard(
    val id: Long,
    val original: String,
    val dictionaryForm: String,
    val translation: String,
    val known: Boolean = false
)

enum class AiProvider { ANTHROPIC, OPENAI, GEMINI }

data class AiSettings(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val apiKey: String = "",
    val model: String = defaultModelFor(AiProvider.ANTHROPIC),
    val baseUrl: String = ""
)

fun defaultModelFor(provider: AiProvider): String = when (provider) {
    AiProvider.ANTHROPIC -> "claude-sonnet-4-6"
    AiProvider.OPENAI -> "gpt-4o-mini"
    AiProvider.GEMINI -> "gemini-2.0-flash"
}
