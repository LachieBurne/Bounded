package com.lburne.bounded.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.decklist.DecklistParser
import com.lburne.bounded.data.decklist.ParsedDecklist
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.rules.DeckCounts
import com.lburne.bounded.data.rules.DeckIssue
import com.lburne.bounded.data.rules.DeckRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportPreview(
    val parsed: ParsedDecklist = ParsedDecklist(),
    val legend: Card? = null,
    val champion: Card? = null,
    val counts: DeckCounts = DeckCounts(0, 0, 0, 0),
    val issues: List<DeckIssue> = emptyList()
)

class DeckImportViewModel(private val repository: CardRepository) : ViewModel() {
    val text = MutableStateFlow("")
    val name = MutableStateFlow("")

    private val cardsById: StateFlow<Map<String, Card>> = repository.allCards
        .map { cards -> cards.associateBy { it.cardId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val preview: StateFlow<ImportPreview> = combine(text, cardsById) { text, cards ->
        if (text.isBlank() || cards.isEmpty()) return@combine ImportPreview()
        val parsed = DecklistParser.parse(text, cards.values)
        ImportPreview(
            parsed = parsed,
            legend = parsed.legendId?.let { cards[it] },
            champion = parsed.championId?.let { cards[it] },
            counts = DeckRules.counts(parsed.contents, cards),
            issues = DeckRules.validate(parsed.contents, cards)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImportPreview())

    private val _imported = Channel<String>()
    val imported = _imported.receiveAsFlow()

    fun importDeck() {
        val result = preview.value
        if (result.parsed.isEmpty) return
        val deckName = name.value.trim().ifEmpty { result.legend?.let { "${it.championTag ?: it.name} deck" } ?: "Imported deck" }
        viewModelScope.launch {
            val id = repository.createDeckFrom(deckName, result.parsed.contents, cardsById.value)
            _imported.send(id)
        }
    }

    class Factory(private val repository: CardRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DeckImportViewModel(repository) as T
    }
}

/** Paste a decklist from another site (or this app's export) and turn it into a deck. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckImportScreen(
    viewModel: DeckImportViewModel,
    onBack: () -> Unit,
    onImported: (String) -> Unit
) {
    val text by viewModel.text.collectAsState()
    val name by viewModel.name.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(viewModel) { viewModel.imported.collect { onImported(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import decklist") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Paste a list with one card per line, like \"3 Master Yi, Honed\". Section headings such as Legend, Champion, Runes and Sideboard are optional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { clipboard.getText()?.text?.let { viewModel.text.value = it } }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Text("Paste from clipboard", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { viewModel.text.value = it },
                    label = { Text("Decklist") },
                    minLines = 8,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.name.value = it },
                    label = { Text("Deck name") },
                    placeholder = { Text(preview.legend?.let { "${it.championTag ?: it.name} deck" } ?: "Imported deck") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!preview.parsed.isEmpty || preview.parsed.unmatched.isNotEmpty()) {
                    ImportSummary(preview)
                }

                Button(
                    onClick = viewModel::importDeck,
                    enabled = !preview.parsed.isEmpty,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import deck") }
            }
        }
    }
}

@Composable
private fun ImportSummary(preview: ImportPreview) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Found", style = MaterialTheme.typography.titleSmall)
            SummaryRow("Legend", preview.legend?.name ?: "None")
            SummaryRow("Champion", preview.champion?.name ?: "None")
            SummaryRow("Main deck", "${preview.counts.mainDeck} cards")
            SummaryRow("Runes", "${preview.counts.runes}")
            SummaryRow("Battlefields", "${preview.counts.battlefields}")
            if (preview.counts.sideboard > 0) SummaryRow("Sideboard", "${preview.counts.sideboard} cards")

            val unmatched = preview.parsed.unmatched
            if (unmatched.isNotEmpty()) {
                Text(
                    if (unmatched.size == 1) "1 line wasn't recognised and will be skipped:" else "${unmatched.size} lines weren't recognised and will be skipped:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                unmatched.take(10).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                if (unmatched.size > 10) Text("and ${unmatched.size - 10} more", style = MaterialTheme.typography.bodySmall)
            }

            if (preview.issues.isNotEmpty() && !preview.parsed.isEmpty) {
                Text(
                    "Not a legal deck yet. You can import it and finish it in the editor:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                preview.issues.take(5).forEach { issue ->
                    Text(
                        "• " + issue.code.message + (issue.subject?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
