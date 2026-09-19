package com.ruflashcards.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.dataStore by preferencesDataStore(name = "ru_flashcards")

private object Keys {
    val WORDS = stringPreferencesKey("words_json")
    val FLASHCARDS = stringPreferencesKey("flashcards_json")
    val PROVIDER = stringPreferencesKey("provider")
    val API_KEY = stringPreferencesKey("api_key")
    val MODEL = stringPreferencesKey("model")
    val BASE_URL = stringPreferencesKey("base_url")
}

class Store(private val context: Context) {

    val wordsFlow: Flow<List<WordItem>> = context.dataStore.data.map { prefs ->
        parseWords(prefs[Keys.WORDS] ?: "[]")
    }

    val flashcardsFlow: Flow<List<Flashcard>> = context.dataStore.data.map { prefs ->
        parseFlashcards(prefs[Keys.FLASHCARDS] ?: "[]")
    }

    val settingsFlow: Flow<AiSettings> = context.dataStore.data.map { prefs ->
        val provider = try {
            AiProvider.valueOf(prefs[Keys.PROVIDER] ?: "ANTHROPIC")
        } catch (e: Exception) {
            AiProvider.ANTHROPIC
        }
        AiSettings(
            provider = provider,
            apiKey = prefs[Keys.API_KEY] ?: "",
            model = prefs[Keys.MODEL] ?: defaultModelFor(provider),
            baseUrl = prefs[Keys.BASE_URL] ?: ""
        )
    }

    suspend fun addWord(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { prefs ->
            val list = parseWords(prefs[Keys.WORDS] ?: "[]").toMutableList()
            val newId = (list.maxOfOrNull { it.id } ?: 0L) + 1
            list.add(WordItem(newId, trimmed))
            prefs[Keys.WORDS] = serializeWords(list)
        }
    }

    suspend fun removeWord(id: Long) {
        context.dataStore.edit { prefs ->
            val list = parseWords(prefs[Keys.WORDS] ?: "[]").filterNot { it.id == id }
            prefs[Keys.WORDS] = serializeWords(list)
        }
    }

    suspend fun clearWords() {
        context.dataStore.edit { prefs -> prefs[Keys.WORDS] = "[]" }
    }

    suspend fun getWordsOnce(): List<WordItem> =
        parseWords(context.dataStore.data.first()[Keys.WORDS] ?: "[]")

    suspend fun addFlashcards(cards: List<Flashcard>) {
        context.dataStore.edit { prefs ->
            val existing = parseFlashcards(prefs[Keys.FLASHCARDS] ?: "[]").toMutableList()
            var nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1
            cards.forEach { c ->
                existing.add(c.copy(id = nextId))
                nextId++
            }
            prefs[Keys.FLASHCARDS] = serializeFlashcards(existing)
        }
    }

    suspend fun updateFlashcard(card: Flashcard) {
        context.dataStore.edit { prefs ->
            val list = parseFlashcards(prefs[Keys.FLASHCARDS] ?: "[]").map {
                if (it.id == card.id) card else it
            }
            prefs[Keys.FLASHCARDS] = serializeFlashcards(list)
        }
    }

    suspend fun deleteFlashcard(id: Long) {
        context.dataStore.edit { prefs ->
            val list = parseFlashcards(prefs[Keys.FLASHCARDS] ?: "[]").filterNot { it.id == id }
            prefs[Keys.FLASHCARDS] = serializeFlashcards(list)
        }
    }

    suspend fun saveSettings(settings: AiSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROVIDER] = settings.provider.name
            prefs[Keys.API_KEY] = settings.apiKey
            prefs[Keys.MODEL] = settings.model
            prefs[Keys.BASE_URL] = settings.baseUrl
        }
    }

    private fun parseWords(json: String): List<WordItem> {
        val arr = JSONArray(json)
        val out = mutableListOf<WordItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(WordItem(o.getLong("id"), o.getString("original")))
        }
        return out
    }

    private fun serializeWords(list: List<WordItem>): String {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(JSONObject().apply {
                put("id", w.id)
                put("original", w.original)
            })
        }
        return arr.toString()
    }

    private fun parseFlashcards(json: String): List<Flashcard> {
        val arr = JSONArray(json)
        val out = mutableListOf<Flashcard>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Flashcard(
                    id = o.getLong("id"),
                    original = o.getString("original"),
                    dictionaryForm = o.getString("dictionaryForm"),
                    translation = o.getString("translation"),
                    known = o.optBoolean("known", false)
                )
            )
        }
        return out
    }

    private fun serializeFlashcards(list: List<Flashcard>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("original", c.original)
                put("dictionaryForm", c.dictionaryForm)
                put("translation", c.translation)
                put("known", c.known)
            })
        }
        return arr.toString()
    }
}
