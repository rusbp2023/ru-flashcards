package com.ruflashcards.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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
    val FLASHCARDS = stringPreferencesKey("flashcards_json") // régi, egy paklis tárolás (migrációhoz)
    val DECKS = stringPreferencesKey("decks_json")
    val ACTIVE_DECK = stringPreferencesKey("active_deck_id")
    val PROVIDER = stringPreferencesKey("provider")
    val API_KEY = stringPreferencesKey("api_key")
    val MODEL = stringPreferencesKey("model")
    val BASE_URL = stringPreferencesKey("base_url")
}

class Store(private val context: Context) {

    val wordsFlow: Flow<List<WordItem>> = context.dataStore.data.map { prefs ->
        parseWords(prefs[Keys.WORDS] ?: "[]")
    }

    val decksFlow: Flow<List<Deck>> = context.dataStore.data.map { prefs ->
        loadDecks(prefs)
    }

    val activeDeckIdFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        activeDeckId(prefs, loadDecks(prefs))
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

    // ---------- szólista ----------

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

    // ---------- kártyák (mindig az AKTÍV pakliban) ----------

    suspend fun addFlashcards(cards: List<Flashcard>) {
        context.dataStore.edit { prefs ->
            val decks = loadDecks(prefs)
            val activeId = activeDeckId(prefs, decks)
            val updated = decks.map { deck ->
                if (deck.id != activeId) deck
                else {
                    val list = deck.cards.toMutableList()
                    var nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1
                    cards.forEach { c ->
                        list.add(c.copy(id = nextId))
                        nextId++
                    }
                    deck.copy(cards = list)
                }
            }
            saveDecks(prefs, updated, activeId)
        }
    }

    suspend fun updateFlashcard(card: Flashcard) {
        context.dataStore.edit { prefs ->
            val decks = loadDecks(prefs)
            val activeId = activeDeckId(prefs, decks)
            val updated = decks.map { deck ->
                if (deck.id != activeId) deck
                else deck.copy(cards = deck.cards.map { if (it.id == card.id) card else it })
            }
            saveDecks(prefs, updated, activeId)
        }
    }

    suspend fun deleteFlashcard(id: Long) {
        context.dataStore.edit { prefs ->
            val decks = loadDecks(prefs)
            val activeId = activeDeckId(prefs, decks)
            val updated = decks.map { deck ->
                if (deck.id != activeId) deck
                else deck.copy(cards = deck.cards.filterNot { it.id == id })
            }
            saveDecks(prefs, updated, activeId)
        }
    }

    // ---------- paklik ----------

    /** Új üres paklit hoz létre, és azonnal aktívvá teszi. */
    suspend fun createDeck(name: String) {
        context.dataStore.edit { prefs ->
            val decks = loadDecks(prefs)
            val newId = (decks.maxOfOrNull { it.id } ?: 0L) + 1
            val deckName = name.trim().ifBlank { "Pakli $newId" }
            saveDecks(prefs, decks + Deck(newId, deckName, emptyList()), newId)
        }
    }

    suspend fun setActiveDeck(id: Long) {
        context.dataStore.edit { prefs ->
            val decks = loadDecks(prefs)
            if (decks.any { it.id == id }) saveDecks(prefs, decks, id)
        }
    }

    suspend fun deleteDeck(id: Long) {
        context.dataStore.edit { prefs ->
            val decks = loadDecks(prefs)
            var remaining = decks.filterNot { it.id == id }
            if (remaining.isEmpty()) {
                val newId = (decks.maxOfOrNull { it.id } ?: 0L) + 1
                remaining = listOf(Deck(newId, "Első pakli", emptyList()))
            }
            val currentActive = activeDeckId(prefs, decks)
            val newActive = if (currentActive == id) remaining.first().id else currentActive
            saveDecks(prefs, remaining, newActive)
        }
    }

    /**
     * A korábban letöltött txt formátumot (egy pakli: "szótári alak — fordítás" soronként,
     * vagy több pakli: "=== Pakli neve ===" fejlécekkel elválasztva) visszatölti.
     * Ha egy adott nevű pakli már létezik, a kártyák abba kerülnek hozzáadásra;
     * ha nem, új pakli jön létre azzal a névvel.
     */
    suspend fun importFromTxt(text: String, fallbackDeckName: String): ImportSummary {
        val parsed = parseImportText(text, fallbackDeckName)
        if (parsed.isEmpty()) return ImportSummary(0, 0)
        var totalCards = 0
        context.dataStore.edit { prefs ->
            val decks = loadDecks(prefs).toMutableList()
            var nextDeckId = (decks.maxOfOrNull { it.id } ?: 0L) + 1
            parsed.forEach { (name, pairs) ->
                val idx = decks.indexOfFirst { it.name == name }
                val startId = if (idx >= 0) {
                    (decks[idx].cards.maxOfOrNull { it.id } ?: 0L) + 1
                } else 1L
                var nid = startId
                val newCards = pairs.map { (dict, trans) ->
                    Flashcard(id = nid++, original = dict, dictionaryForm = dict, translation = trans)
                }
                totalCards += newCards.size
                if (idx >= 0) {
                    decks[idx] = decks[idx].copy(cards = decks[idx].cards + newCards)
                } else {
                    decks.add(Deck(nextDeckId, name, newCards))
                    nextDeckId++
                }
            }
            val activeId = activeDeckId(prefs, decks)
            saveDecks(prefs, decks, activeId)
        }
        return ImportSummary(parsed.size, totalCards)
    }

    private fun parseImportText(
        text: String,
        fallbackDeckName: String
    ): List<Pair<String, List<Pair<String, String>>>> {
        val headerRegex = Regex("^===\\s*(.+?)\\s*===$")
        val lines = text.replace("\r\n", "\n").split("\n")
        val hasHeaders = lines.any { headerRegex.matches(it.trim()) }
        val result = mutableListOf<Pair<String, MutableList<Pair<String, String>>>>()

        if (hasHeaders) {
            var currentList: MutableList<Pair<String, String>>? = null
            for (raw in lines) {
                val line = raw.trim()
                val m = headerRegex.matchEntire(line)
                if (m != null) {
                    val name = m.groupValues[1].trim()
                    val list = mutableListOf<Pair<String, String>>()
                    result.add(name to list)
                    currentList = list
                } else if (line.isNotEmpty()) {
                    currentList?.let { list -> parseCardLine(line)?.let { list.add(it) } }
                }
            }
        } else {
            val list = mutableListOf<Pair<String, String>>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                parseCardLine(line)?.let { list.add(it) }
            }
            if (list.isNotEmpty()) result.add(fallbackDeckName.ifBlank { "Importált pakli" } to list)
        }

        return result.filter { it.second.isNotEmpty() }
    }

    private fun parseCardLine(line: String): Pair<String, String>? {
        val parts = line.split(Regex("\\s*—\\s*"), limit = 2)
        if (parts.size != 2) return null
        val dict = parts[0].trim()
        val trans = parts[1].trim()
        if (dict.isEmpty() || trans.isEmpty()) return null
        return dict to trans
    }

    suspend fun saveSettings(settings: AiSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROVIDER] = settings.provider.name
            prefs[Keys.API_KEY] = settings.apiKey
            prefs[Keys.MODEL] = settings.model
            prefs[Keys.BASE_URL] = settings.baseUrl
        }
    }

    // ---------- belső segédfüggvények ----------

    /** Ha még nincs elmentett paklilista, a régi (egy paklis) kártyákból csinál egy "Első pakli"-t. */
    private fun loadDecks(prefs: Preferences): List<Deck> {
        val raw = prefs[Keys.DECKS]
        if (raw != null) {
            val parsed = parseDecks(raw)
            if (parsed.isNotEmpty()) return parsed
        }
        val legacy = parseFlashcards(prefs[Keys.FLASHCARDS] ?: "[]")
        return listOf(Deck(1L, "Első pakli", legacy))
    }

    private fun activeDeckId(prefs: Preferences, decks: List<Deck>): Long {
        val saved = prefs[Keys.ACTIVE_DECK]?.toLongOrNull()
        return if (saved != null && decks.any { it.id == saved }) saved else decks.first().id
    }

    private fun saveDecks(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        decks: List<Deck>,
        activeId: Long
    ) {
        prefs[Keys.DECKS] = serializeDecks(decks)
        prefs[Keys.ACTIVE_DECK] = activeId.toString()
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

    private fun parseCard(o: JSONObject) = Flashcard(
        id = o.getLong("id"),
        original = o.getString("original"),
        dictionaryForm = o.getString("dictionaryForm"),
        translation = o.getString("translation"),
        known = o.optBoolean("known", false)
    )

    private fun cardToJson(c: Flashcard) = JSONObject().apply {
        put("id", c.id)
        put("original", c.original)
        put("dictionaryForm", c.dictionaryForm)
        put("translation", c.translation)
        put("known", c.known)
    }

    private fun parseFlashcards(json: String): List<Flashcard> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { parseCard(arr.getJSONObject(it)) }
    }

    private fun parseDecks(json: String): List<Deck> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val cardsArr = o.getJSONArray("cards")
            Deck(
                id = o.getLong("id"),
                name = o.getString("name"),
                cards = (0 until cardsArr.length()).map { parseCard(cardsArr.getJSONObject(it)) }
            )
        }
    }

    private fun serializeDecks(list: List<Deck>): String {
        val arr = JSONArray()
        list.forEach { d ->
            val cardsArr = JSONArray()
            d.cards.forEach { cardsArr.put(cardToJson(it)) }
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("cards", cardsArr)
            })
        }
        return arr.toString()
    }
}
