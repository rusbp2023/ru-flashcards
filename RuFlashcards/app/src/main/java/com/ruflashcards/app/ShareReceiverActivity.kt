package com.ruflashcards.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ruflashcards.app.data.Store
import kotlinx.coroutines.launch

/**
 * Ezt az Activity-t hívja meg Android, amikor a Chrome-ban kijelölt szöveget
 * "Megosztás"-sal elküldöd az appnak. Nincs saját UI-ja: elmenti a szót,
 * mutat egy Toast-ot, majd bezárja magát.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Nem sikerült beolvasni a kijelölt szöveget", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val cleaned = cleanSharedText(text)

        val store = Store(applicationContext)
        lifecycleScope.launch {
            store.addWord(cleaned)
            Toast.makeText(
                this@ShareReceiverActivity,
                "Hozzáadva: $cleaned",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    /**
     * A Chrome a kijelölt szöveg mellé gyakran hozzáfűzi az oldal URL-jét is
     * (külön sorban). Ez a függvény ezt eldobja, és csak a tényleges
     * kijelölt szót/kifejezést adja vissza.
     */
    private fun cleanSharedText(raw: String): String {
        val urlRegex = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
        val firstNonUrlLine = raw.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !urlRegex.matches(it) }
        return (firstNonUrlLine ?: raw.trim())
    }
}
