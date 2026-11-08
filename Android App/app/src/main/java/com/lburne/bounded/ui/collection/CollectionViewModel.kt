package com.lburne.bounded.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.filter.CardFilter
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.Binder
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository
import com.lburne.bounded.ui.common.RiftSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** How much of a binder you own. [value] is in the user's currency. */
data class BinderProgress(val owned: Int = 0, val total: Int = 0, val copies: Int = 0, val value: Double = 0.0) {
    val fraction: Float get() = if (total == 0) 0f else owned.toFloat() / total
    val percent: Int get() = (fraction * 100).toInt()
}

fun progressOf(cards: List<CardWithQuantity>, scope: CardFilter, prices: Map<String, Double>, rate: Double): BinderProgress {
    var owned = 0
    var total = 0
    var copies = 0
    var value = 0.0
    cards.forEach { item ->
        if (!scope.matches(item.card, item.quantity)) return@forEach
        total++
        if (item.quantity > 0) {
            owned++
            copies += item.quantity
            value += (prices[item.card.cardId] ?: 0.0) * item.quantity
        }
    }
    return BinderProgress(owned, total, copies, value * rate)
}

data class BinderSummary(
    val binder: Binder?, // null for the whole collection
    val name: String,
    val description: String,
    val progress: BinderProgress,
    /** The most valuable card in the binder, to illustrate it. */
    val cover: Card?
)

data class CollectionUiState(
    val isLoading: Boolean = true,
    val collection: BinderSummary? = null,
    val binders: List<BinderSummary> = emptyList(),
    /** Sets that don't have a master set binder yet, offered as one-tap suggestions. */
    val suggestedSets: List<RiftSet> = emptyList(),
    val currencySymbol: String = "$"
)

class CollectionViewModel(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository
) : ViewModel() {

    val uiState: StateFlow<CollectionUiState> = combine(
        repository.getBinderCards(Binder.MAIN_ID),
        repository.binders,
        priceRepository.prices,
        priceRepository.rate
    ) { cards, binders, prices, rate ->
        fun summarise(binder: Binder?, name: String, description: String, scope: CardFilter): BinderSummary {
            val cover = cards.asSequence()
                .filter { scope.matches(it.card, it.quantity) }
                .maxByOrNull { prices[it.card.cardId] ?: 0.0 }
                ?.card
            return BinderSummary(binder, name, description, progressOf(cards, scope, prices, rate), cover)
        }

        val coveredSets = binders.filter { it.sets.size == 1 }.map { it.sets.first() }.toSet()
        CollectionUiState(
            isLoading = false,
            collection = summarise(null, "All cards", "Every card in every set", CardFilter()),
            binders = binders.map { summarise(it, it.name, it.describe(RiftSet.namesByCode), it.scope()) },
            suggestedSets = RiftSet.values().filter { it.code !in coveredSets },
            currencySymbol = priceRepository.getCurrencySymbol()
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CollectionUiState())

    private val allCards: StateFlow<List<Card>> = repository.allCards
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** How many cards a binder definition covers, for the editor's preview. */
    fun countFor(binder: Binder): Int {
        val scope = binder.scope()
        return allCards.value.count { scope.matches(it, 0) }
    }

    fun saveBinder(binder: Binder) = repository.saveBinder(binder)

    fun deleteBinder(binderId: String) = repository.deleteBinder(binderId)

    /** One tap from the suggestions: a binder for every printing in one set. */
    fun createMasterSet(set: RiftSet) =
        repository.saveBinder(Binder(name = "${set.displayName} master set", sets = listOf(set.code)))

    class Factory(private val repository: CardRepository, private val priceRepository: PriceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CollectionViewModel(repository, priceRepository) as T
    }
}
