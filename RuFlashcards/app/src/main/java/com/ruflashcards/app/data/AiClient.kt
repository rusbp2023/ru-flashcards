package com.ruflashcards.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Egy AiClient hívást intéz az adott szolgáltatóhoz (Anthropic / OpenAI / Gemini),
 * és a kapott orosz szólistából szótári alak + magyar fordítás párokat állít elő.
 */
class AiClient(private val settings: AiSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    /** Blokkoló hívás — mindig háttérszálon (Dispatchers.IO) hívd! */
    fun lookupWords(words: List<String>): List<Flashcard> {
        if (words.isEmpty()) return emptyList()
        if (settings.apiKey.isBlank()) throw IllegalStateException("Nincs megadva API kulcs a Beállításoknál.")

        val prompt = buildPrompt(words)
        val rawText = when (settings.provider) {
            AiProvider.ANTHROPIC -> callAnthropic(prompt)
            AiProvider.OPENAI -> callOpenAi(prompt)
            AiProvider.GEMINI -> callGemini(prompt)
        }
        return parseResponse(rawText, words)
    }

        private fun buildPrompt(words: List<String>): String {
        val list = words.joinToString("\n") { "- $it" }
        return """
            A következő orosz szavak ragozott/toldalékolt alakban vannak megadva, egy weboldalról kimásolva.
            Minden szóhoz add meg:
            1. a szótári alapalakot (ige esetén infinitivus, főnév esetén egyes szám alanyeset, stb.)
            2. a legjellemzőbb magyar fordítást a szótári alakhoz, röviden.

            A "translation" mezőbe KIZÁRÓLAG a magyar szót/kifejezést írd, semmi mást.
            NE tedd bele az eredeti orosz szó nyelvtani elemzését (pl. hogy milyen esetben,
            számban vagy igeidőben áll az eredeti alak) — az a "dictionary_form" mezőtől független,
            és a "translation" mezőben nem kell megjelennie zárójelben vagy bármilyen más formában.
            Például ha az eredeti szó "домов" (birtokos eset, többes szám), a dictionary_form "дом",
            a translation pedig egyszerűen "ház" legyen — nem "ház (birtokos eset, többes szám)".

            Válaszolj KIZÁRÓLAG egy JSON tömbbel, semmi mást ne írj a válaszba (se magyarázatot, se code fence-t).
            A formátum pontosan ez legyen:
            [{"original":"...","dictionary_form":"...","translation":"..."}]

            A szavak:
            $list
        """.trimIndent()
    }

    private fun callAnthropic(prompt: String): String {
        val body = JSONObject().apply {
            put("model", settings.model.ifBlank { "claude-sonnet-4-6" })
            put("max_tokens", 2000)
            put(
                "messages",
                JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            )
        }
        val url = settings.baseUrl.ifBlank { "https://api.anthropic.com/v1/messages" }
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", settings.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("Anthropic hiba (${resp.code}): $respBody")
            val json = JSONObject(respBody)
            val content = json.getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") sb.append(block.getString("text"))
            }
            return sb.toString()
        }
    }

    private fun callOpenAi(prompt: String): String {
        val body = JSONObject().apply {
            put("model", settings.model.ifBlank { "gpt-4o-mini" })
            put(
                "messages",
                JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            )
        }
        val url = settings.baseUrl.ifBlank { "https://api.openai.com/v1/chat/completions" }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("OpenAI hiba (${resp.code}): $respBody")
            val json = JSONObject(respBody)
            val choices = json.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            return message.getString("content")
        }
    }

    private fun callGemini(prompt: String): String {
        val model = settings.model.ifBlank { "gemini-2.0-flash" }
        val url = settings.baseUrl.ifBlank {
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${settings.apiKey}"
        }
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
                })
            )
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("Gemini hiba (${resp.code}): $respBody")
            val json = JSONObject(respBody)
            val candidates = json.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text")
        }
    }

    private fun parseResponse(raw: String, originalWords: List<String>): List<Flashcard> {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val startIdx = cleaned.indexOf('[')
        val endIdx = cleaned.lastIndexOf(']')
        if (startIdx == -1 || endIdx == -1) {
            throw RuntimeException("Nem sikerült értelmezni az AI válaszát: $cleaned")
        }
        val jsonPart = cleaned.substring(startIdx, endIdx + 1)
        val arr = JSONArray(jsonPart)
        val out = mutableListOf<Flashcard>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Flashcard(
                    id = 0L,
                    original = o.optString("original", originalWords.getOrElse(i) { "" }),
                    dictionaryForm = o.optString("dictionary_form", ""),
                    translation = o.optString("translation", "")
                )
            )
        }
        return out
    }
}
