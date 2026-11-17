package com.lburne.bounded.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.Finish
import com.lburne.bounded.data.model.finishes
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One version of a card you can own and count separately: a finish of the card (standard,
 * foil, and promos such as Nexus Night or a Summoner Skirmish Top 8), or the signature printing
 * of an overnumbered card, which is a card of its own with the same number.
 */
data class CardVariant(
    val item: CardWithQuantity,
    val finish: Finish,
    val label: String,
    val price: Double?
) {
    val card: Card get() = item.card
    val owned: Int get() = item.count(finish)
}

class CardDetailViewModel(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository,
    private val cardId: String
) : ViewModel() {

    val uiState: StateFlow<CardDetailUiState> = combine(
        repository.getBinderCards("main"),
        priceRepository.prices,
        priceRepository.rate // Re-convert when the currency or exchange rates change
    ) { cards, prices, rate ->
        if (cards.isEmpty()) return@combine CardDetailUiState.Loading
        val byId = cards.associateBy { it.card.cardId }
        val opened = byId[cardId] ?: return@combine CardDetailUiState.Error

        // "OGN-299" and its signature "OGN-299*" are shown together, the plain one first.
        val baseId = cardId.removeSuffix("*")
        val base = byId[baseId] ?: opened
        val signature = byId["$baseId*"]

        fun variant(item: CardWithQuantity, finish: Finish, label: String) =
            CardVariant(item, finish, label, prices[item.card.cardId]?.let { it * rate })

        val variants = if (signature != null && base !== signature) {
            listOf(variant(base, Finish.STANDARD, "Overnumbered"), variant(signature, Finish.STANDARD, "Signature"))
        } else {
            opened.card.finishes.map { variant(opened, it, it.label) }
        }
        CardDetailUiState.Success(variants, variants.indexOfFirst { it.card.cardId == cardId }.coerceAtLeast(0))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CardDetailUiState.Loading
    )

    fun increment(variant: CardVariant) = change(variant, 1)

    fun decrement(variant: CardVariant) {
        if (variant.owned > 0) change(variant, -1)
    }

    private fun change(variant: CardVariant, by: Int) {
        viewModelScope.launch {
            repository.addCopies("main", mapOf(variant.card.cardId to mapOf(variant.finish to by)))
        }
    }

    fun getCurrencySymbol(): String {
        return priceRepository.getCurrencySymbol()
    }
}

sealed interface CardDetailUiState {
    data object Loading : CardDetailUiState
    data object Error : CardDetailUiState

    /** The card's [variants], to swipe between, and the one that was opened. */
    data class Success(
        val variants: List<CardVariant>,
        val openedIndex: Int
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
