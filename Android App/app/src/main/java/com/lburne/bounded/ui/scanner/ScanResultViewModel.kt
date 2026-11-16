package com.lburne.bounded.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.local.AppDatabase
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.Copies
import com.lburne.bounded.data.model.Finish
import com.lburne.bounded.data.model.finishes
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScanResultViewModel(
    application: Application,
    private val cardRepository: CardRepository,
    private val priceRepository: PriceRepository
) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val cardDao = database.cardDao()

    private val _scannedCards = MutableStateFlow<List<Card>>(emptyList())
    val scannedCards: StateFlow<List<Card>> = _scannedCards

    private val _selectedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIndices: StateFlow<Set<Int>> = _selectedIndices

    /**
     * For an overnumbered card, its signature printing ("OGN-299" and "OGN-299*"), and the
     * other way round. The two share a number, so the scanner can read one as the other.
     */
    private val _signatureSiblings = MutableStateFlow<Map<String, Card>>(emptyMap())
    val signatureSiblings: StateFlow<Map<String, Card>> = _signatureSiblings

    /**
     * The finish chosen for each scanned card, by its place in the list. A foil has the same
     * number as the standard printing, so the scanner can't tell; anything not here is standard.
     */
    private val _finishes = MutableStateFlow<Map<Int, Finish>>(emptyMap())
    val finishes: StateFlow<Map<Int, Finish>> = _finishes

    /** Marks a scanned card as [finish], or back to standard if it already is. */
    fun toggleFinish(index: Int, finish: Finish) {
        val card = _scannedCards.value.getOrNull(index) ?: return
        if (finish !in card.finishes) return
        _finishes.value = if (_finishes.value[index] == finish) _finishes.value - index else _finishes.value + (index to finish)
    }

    /** Swaps a scanned card for its signature printing, or back. */
    fun toggleSignature(index: Int) {
        val cards = _scannedCards.value
        val sibling = cards.getOrNull(index)?.let { _signatureSiblings.value[it.cardId] } ?: return
        _scannedCards.value = cards.toMutableList().also { it[index] = sibling }
    }

    fun loadScannedCards(cardIds: List<String>) {
        viewModelScope.launch {
            val loadedCards = mutableListOf<Card>()
            val siblings = mutableMapOf<String, Card>()
            for (id in cardIds) {
                val card = cardDao.getCardDefinition(id)
                if (card != null) {
                    loadedCards.add(card)
                    val siblingId = if (id.endsWith("*")) id.dropLast(1) else "$id*"
                    cardDao.getCardDefinition(siblingId)?.let { sibling ->
                        siblings[card.cardId] = sibling
                        siblings[sibling.cardId] = card
                    }
                }
            }
            _signatureSiblings.value = siblings
            _scannedCards.value = loadedCards
            // Default to all selected
            _selectedIndices.value = loadedCards.indices.toSet()
        }
    }

    fun toggleSelection(index: Int) {
        val currentSelection = _selectedIndices.value.toMutableSet()
        if (currentSelection.contains(index)) {
            currentSelection.remove(index)
        } else {
            currentSelection.add(index)
        }
        _selectedIndices.value = currentSelection
    }

    fun selectAll() {
        _selectedIndices.value = _scannedCards.value.indices.toSet()
    }

    fun deselectAll() {
        _selectedIndices.value = emptySet()
    }

    fun addSelectedToBinder(binderId: String?, onComplete: () -> Unit) {
        viewModelScope.launch {
            val targetBinderId = binderId ?: "main" // Riftbounder uses 'main'
            // Add each card's copies in one write. Reading and writing per copy lost copies,
            // because the second read came back before the first write showed up.
            val copiesById = mutableMapOf<String, Copies>()
            _scannedCards.value.forEachIndexed { index, card ->
                if (index !in _selectedIndices.value) return@forEachIndexed
                val one = Copies.of(_finishes.value[index] ?: Finish.STANDARD, 1)
                val soFar = copiesById[card.cardId] ?: Copies()
                copiesById[card.cardId] = Copies(soFar.standard + one.standard, soFar.foil + one.foil, soFar.nexusNight + one.nexusNight)
            }
            cardRepository.addCopies(targetBinderId, copiesById)
            onComplete()
        }
    }

    fun getPriceForCard(cardId: String): Double? {
        val prices = priceRepository.prices.value
        return prices[cardId]?.let { priceRepository.convertPrice(it) }
    }

    fun getCurrencySymbol(): String {
        return priceRepository.getCurrencySymbol()
    }

    class Factory(
        private val application: Application,
        private val cardRepository: CardRepository,
        private val priceRepository: PriceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScanResultViewModel::class.java)) {
                return ScanResultViewModel(application, cardRepository, priceRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
