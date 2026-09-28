package com.ruflashcards.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruflashcards.app.data.AiClient
import com.ruflashcards.app.data.AiSettings
import com.ruflashcards.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { WORDS, CARDS, DECKS, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            MaterialTheme {
                AppRoot(store)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(store: Store) {
    var screen by remember { mutableStateOf(Screen.WORDS) }
    val words by store.wordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val decks by store.decksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeDeckId by store.activeDeckIdFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val settings by store.settingsFlow.collectAsStateWithLifecycle(initialValue = AiSettings())
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val activeDeck = decks.find { it.id == activeDeckId } ?: decks.firstOrNull()
    val cards = activeDeck?.cards ?: emptyList()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Orosz Szókártyák") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == Screen.WORDS,
                    onClick = { screen = Screen.WORDS },
                    icon = {},
                    label = { Text("Szólista (${words.size})") }
                )
                NavigationBarItem(
                    selected = screen == Screen.CARDS,
                    onClick = { screen = Screen.CARDS },
                    icon = {},
                    label = { Text("Kártyák (${cards.size})") }
                )
                NavigationBarItem(
                    selected = screen == Screen.DECKS,
                    onClick = { screen = Screen.DECKS },
                    icon = {},
                    label = { Text("Paklik (${decks.size})") }
                )
                NavigationBarItem(
                    selected = screen == Screen.SETTINGS,
                    onClick = { screen = Screen.SETTINGS },
                    icon = {},
                    label = { Text("Beállítások") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (screen) {
                Screen.WORDS -> WordListScreen(
                    words = words,
                    loading = loading,
                    onDelete = { id -> scope.launch { store.removeWord(id) } },
                    onClear = { scope.launch { store.clearWords() } },
                    onAddManual = { text -> scope.launch { store.addWord(text) } },
                    onGenerate = {
                        if (settings.apiKey.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Előbb add meg az API kulcsot a Beállításoknál!")
                            }
                        } else {
                            loading = true
                            scope.launch {
                                try {
                                    val originals = words.map { it.original }
                                    val client = AiClient(settings)
                                    val result = withContext(Dispatchers.IO) { client.lookupWords(originals) }
                                    store.addFlashcards(result) // az aktív paklihoz adja hozzá
                                    store.clearWords()
                                    screen = Screen.CARDS
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Hiba: ${e.message}")
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    }
                )

                Screen.CARDS -> Column(Modifier.fillMaxSize()) {
                    Text(
                        "Aktív pakli: ${activeDeck?.name ?: "-"}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                    )
                    Box(Modifier.weight(1f)) {
                        FlashcardScreen(
                            cards = cards,
                            onDelete = { id -> scope.launch { store.deleteFlashcard(id) } },
                            onToggleKnown = { c -> scope.launch { store.updateFlashcard(c.copy(known = !c.known)) } }
                        )
                    }
                }

                Screen.DECKS -> DecksScreen(
                    decks = decks,
                    activeDeckId = activeDeck?.id ?: 0L,
                    onSelect = { id ->
                        scope.launch { store.setActiveDeck(id) }
                        screen = Screen.CARDS
                    },
                    onCreate = { name -> scope.launch { store.createDeck(name) } },
                    onDelete = { id -> scope.launch { store.deleteDeck(id) } }
                )

                Screen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onSave = { s -> scope.launch { store.saveSettings(s) } }
                )
            }
        }
    }
}
