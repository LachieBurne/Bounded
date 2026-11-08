package com.lburne.bounded.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.local.DeckCardView
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Deck
import com.lburne.bounded.data.model.DeckFormat
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.repository.CardRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

/** A deck in the list, with the legend's domains for its badge. */
data class DeckSummary(val deck: Deck, val legendDomains: List<Domain>)

class DeckListViewModel(private val repository: CardRepository) : ViewModel() {

    private val cardsById = repository.allCards.map { cards -> cards.associateBy { it.cardId } }

    val decks: StateFlow<List<DeckSummary>?> = combine(repository.allDecks, cardsById) { decks, cards ->
        decks.map { DeckSummary(it, it.legendCardId?.let { id -> cards[id]?.domain } ?: emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Opens a deck after it's created or copied.
    private val _navigationEvent = Channel<String>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun createDeck(name: String, format: DeckFormat) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                val newId = repository.createDeck(name.trim(), format)
                _navigationEvent.send(newId)
            }
        }
    }

    fun duplicateDeck(deck: Deck) {
        viewModelScope.launch {
            val newId = repository.duplicateDeck(deck, "${deck.name} (copy)")
            _navigationEvent.send(newId)
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
                repository.renameDeck(deckId, newName.trim())
            }
        }
    }

    suspend fun getExportText(deckId: String): String {
        val deck = repository.getDeck(deckId).firstOrNull() ?: return ""
        val cards = repository.getDeckCards(deckId).first()
        return exportDecklistToText(deck, cards.main, cards.side)
    }
}

class DeckListViewModelFactory(private val repository: CardRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DeckListViewModel(repository) as T
    }
}