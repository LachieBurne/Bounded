package com.lburne.bounded.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.local.DeckCardView
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Deck
import com.lburne.bounded.data.model.DeckFormat
import com.lburne.bounded.data.repository.CardRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// --- SHARED HELPER FOR TEXT EXPORT ---
fun exportDecklistToText(deck: Deck, mainSlots: List<DeckCardView>, sideSlots: List<DeckCardView>): String = buildString {
    // 1. Legend
    val legend = mainSlots.find { it.card.cardId == deck.legendCardId }
    if (legend != null) {
        append("Legend:\n1 ${legend.card.name}\n\n")
    }

    // 2. Champion
    val champion = mainSlots.find { it.card.cardId == deck.championCardId }
    if (champion != null) {
        append("Champion:\n1 ${champion.card.name}\n\n")
    }

    // 3. MainDeck
    val mainDeck = mainSlots.filter {
        it.card.cardId != deck.legendCardId &&
                it.card.type != CardType.RUNE &&
                it.card.type != CardType.BATTLEFIELD
    }.mapNotNull { slot ->
        // Subtract 1 copy if this is the chosen champion to avoid double counting
        if (slot.card.cardId == deck.championCardId) {
            if (slot.deckQuantity > 1) slot.copy(deckQuantity = slot.deckQuantity - 1) else null
        } else {
            slot
        }
    }
    if (mainDeck.isNotEmpty()) {
        append("MainDeck:\n")
        mainDeck.forEach { append("${it.deckQuantity} ${it.card.name}\n") }
        append("\n")
    }

    // 4. Battlefields
    val battlefields = mainSlots.filter { it.card.type == CardType.BATTLEFIELD }
    if (battlefields.isNotEmpty()) {
        append("Battlefields:\n")
        battlefields.forEach { append("${it.deckQuantity} ${it.card.name}\n") }
        append("\n")
    }

    // 5. Runes
    val runes = mainSlots.filter { it.card.type == CardType.RUNE }
    if (runes.isNotEmpty()) {
        append("Runes:\n")
        runes.forEach { append("${it.deckQuantity} ${it.card.name}\n") }
        append("\n")
    }

    // 6. Sideboard
    if (sideSlots.isNotEmpty()) {
        append("Sideboard:\n")
        sideSlots.forEach { append("${it.deckQuantity} ${it.card.name}\n") }
        append("\n")
    }
}.trimEnd()

class DeckListViewModel(private val repository: CardRepository) : ViewModel() {

    // Hot flow of all decks
    val decks: StateFlow<List<Deck>> = repository.allDecks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Create a Navigation Event Channel
    private val _navigationEvent = Channel<String>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    // Update createDeck to send the event
    fun createDeck(name: String, format: DeckFormat) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                val newId = repository.createDeck(name, format)
                _navigationEvent.send(newId) // Trigger Navigation
            }
        }
    }

    fun deleteDeck(deck: Deck) {
        viewModelScope.launch {
            repository.deleteDeck(deck)
        }
    }

    fun renameDeck(deckId: String, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                repository.renameDeck(deckId, newName)
            }
        }
    }

    suspend fun getExportText(deckId: String): String {
        val deck = repository.getDeck(deckId).firstOrNull() ?: return ""
        val main = repository.getMainDeckCards(deckId).first()
        val side = repository.getSideboardCards(deckId).first()
        return exportDecklistToText(deck, main, side)
    }
}

class DeckListViewModelFactory(private val repository: CardRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DeckListViewModel(repository) as T
    }
}