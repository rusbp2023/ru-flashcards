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

        val store = Store(applicationContext)
        lifecycleScope.launch {
            store.addWord(text)
            Toast.makeText(
                this@ShareReceiverActivity,
                "Hozzáadva: ${text.trim()}",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}
