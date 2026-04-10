package com.lburne.bounded.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardDetailViewModel(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository,
    private val cardId: String
) : ViewModel() {

    val uiState: StateFlow<CardDetailUiState> = combine(
        repository.getCardWithQuantity(cardId, "main"),
        priceRepository.prices // <--- Listen to price updates
    ) { cardWithQuantity, prices ->
        if (cardWithQuantity != null) {
            val price = priceRepository.convertPrice(prices[cardId] ?: 0.0)
            CardDetailUiState.Success(cardWithQuantity, price)
        } else {
            CardDetailUiState.Error
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CardDetailUiState.Loading
    )

    fun incrementQuantity() {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState is CardDetailUiState.Success) {
                repository.updateCardQuantity("main", cardId, currentState.card.quantity + 1)
            }
        }
    }

    fun decrementQuantity() {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState is CardDetailUiState.Success) {
                if (currentState.card.quantity > 0) {
                    repository.updateCardQuantity("main", cardId, currentState.card.quantity - 1)
                }
            }
        }
    }

    fun getCurrencySymbol(): String {
        return priceRepository.getCurrencySymbol()
    }
}

sealed interface CardDetailUiState {
    data object Loading : CardDetailUiState
    data object Error : CardDetailUiState
    data class Success(
        val card: CardWithQuantity,
        val price: Double? = null
    ) : CardDetailUiState
}

class CardDetailViewModelFactory(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository,
    private val cardId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CardDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CardDetailViewModel(repository, priceRepository, cardId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}