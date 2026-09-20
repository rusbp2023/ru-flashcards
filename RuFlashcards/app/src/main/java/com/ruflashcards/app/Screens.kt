package com.ruflashcards.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ruflashcards.app.data.AiProvider
import com.ruflashcards.app.data.AiSettings
import com.ruflashcards.app.data.Flashcard
import com.ruflashcards.app.data.WordItem
import com.ruflashcards.app.data.defaultModelFor

@Composable
fun WordListScreen(
    words: List<WordItem>,
    loading: Boolean,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onAddManual: (String) -> Unit,
    onGenerate: () -> Unit
) {
    var manualText by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Jelölj ki egy szót a Chrome-ban a weboldalon, majd Megosztás → Orosz Szókártyák. " +
                "A szó itt jelenik meg a listában.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualText,
                onValueChange = { manualText = it },
                label = { Text("Szó kézzel hozzáadása") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                onAddManual(manualText)
                manualText = ""
            }) { Text("+") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(words, key = { it.id }) { w ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(w.original, style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { onDelete(w.id) }) {
                        Text("✕")
                    }
                }
                Divider()
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text("Lista ürítése")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onGenerate,
                enabled = words.isNotEmpty() && !loading,
                modifier = Modifier.weight(1f)
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Kártyák generálása (${words.size})")
                }
            }
        }
    }
}

@Composable
fun FlashcardScreen(
    cards: List<Flashcard>,
    onDelete: (Long) -> Unit,
    onToggleKnown: (Flashcard) -> Unit
) {
    if (cards.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Még nincs szókártyád. Adj hozzá szavakat a Szólista fülön, majd generáld le őket.",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    var index by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val safeIndex = index.coerceIn(0, cards.size - 1)
    val card = cards[safeIndex]

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("${safeIndex + 1} / ${cards.size}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            onClick = { flipped = !flipped }
        ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!flipped) {
                        Text(card.translation, style = MaterialTheme.typography.headlineMedium)
                    } else {
                        Text(card.dictionaryForm, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = {
                onDelete(card.id)
                flipped = false
            }) { Text("Törlés") }
            OutlinedButton(onClick = { onToggleKnown(card) }) {
                Text(if (card.known) "Tudom ✓" else "Megjelöl: tudom")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                flipped = false
                index = (safeIndex - 1 + cards.size) % cards.size
            }) { Text("◀ Előző") }
            Button(onClick = {
                flipped = false
                index = (safeIndex + 1) % cards.size
            }) { Text("Következő ▶") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AiSettings,
    onSave: (AiSettings) -> Unit
) {
    var provider by remember(settings) { mutableStateOf(settings.provider) }
    var apiKey by remember(settings) { mutableStateOf(settings.apiKey) }
    var model by remember(settings) { mutableStateOf(settings.model) }
    var baseUrl by remember(settings) { mutableStateOf(settings.baseUrl) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("AI szolgáltató", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = provider.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Szolgáltató") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            DropdownMenu(
    expanded = expanded,
    onDismissRequest = { expanded = false }
) {
                AiProvider.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.name) },
                        onClick = {
                            provider = p
                            model = defaultModelFor(p)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API kulcs") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Modell neve") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Egyedi API végpont (opcionális)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onSave(AiSettings(provider, apiKey, model, baseUrl)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Mentés")
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Tipp: az API kulcsot a szolgáltató oldalán kapod (pl. console.anthropic.com, " +
                "platform.openai.com, aistudio.google.com). A kulcs csak a telefonodon tárolódik, " +
                "az AI-nak közvetlenül a telefon küldi el a kéréseket.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
