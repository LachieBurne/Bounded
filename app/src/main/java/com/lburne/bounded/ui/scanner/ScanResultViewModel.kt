package com.lburne.bounded.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.local.AppDatabase
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    fun loadScannedCards(cardIds: List<String>) {
        viewModelScope.launch {
            val loadedCards = mutableListOf<Card>()
            for (id in cardIds) {
                val card = cardDao.getCardDefinition(id)
                if (card != null) {
                    loadedCards.add(card)
                }
            }
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
            val selectedCards = _scannedCards.value.filterIndexed { index, _ -> _selectedIndices.value.contains(index) }
            
            for (card in selectedCards) {
                try {
                    val currentCardQty = cardRepository.getCardWithQuantity(card.cardId, targetBinderId).first()
                    cardRepository.updateCardQuantity(targetBinderId, card.cardId, currentCardQty.quantity + 1)
                } catch(e: Exception) {
                    // Fallback to 1 if card didn't exist in binder yet
                    cardRepository.updateCardQuantity(targetBinderId, card.cardId, 1)
                }
            }
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
