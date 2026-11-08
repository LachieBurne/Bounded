package com.lburne.bounded.ui.binder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.filter.CardFilter
import com.lburne.bounded.data.filter.CardSort
import com.lburne.bounded.data.filter.sortedForDisplay
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.Binder
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository
import com.lburne.bounded.ui.collection.BinderProgress
import com.lburne.bounded.ui.collection.progressOf
import com.lburne.bounded.ui.common.RiftSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BinderUiState(
    val isLoading: Boolean = true,
    /** null for the whole collection. */
    val binder: Binder? = null,
    /** The binder was deleted (perhaps on another device). */
    val isMissing: Boolean = false,
    val title: String = "All cards",
    val progress: BinderProgress = BinderProgress(),
    val cards: List<CardWithQuantity> = emptyList(),
    val filter: CardFilter = CardFilter(),
    val sort: CardSort = CardSort.SET
)

/**
 * One binder's grid. [binderId] is [Binder.MAIN_ID] for the whole collection. Quantities
 * always come from the collection, so a change here shows in every binder.
 */
class BinderViewModel(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository,
    private val binderId: String
) : ViewModel() {

    private val filter = MutableStateFlow(CardFilter())
    private val sort = MutableStateFlow(CardSort.SET)

    private val binder = if (binderId == Binder.MAIN_ID) flowOf(null) else
        repository.binders.map { list -> list.firstOrNull { it.binderId == binderId } }

    private val view = combine(filter, sort) { f, s -> f to s }

    val uiState: StateFlow<BinderUiState> = combine(
        repository.getBinderCards(Binder.MAIN_ID),
        binder,
        view,
        priceRepository.prices,
        priceRepository.rate
    ) { cards, binder, (userFilter, sortOrder), prices, rate ->
        val isMissing = binderId != Binder.MAIN_ID && binder == null
        val scope = binder?.scope() ?: CardFilter()
        val shown = cards
            .filter { scope.matches(it.card, it.quantity) && userFilter.matches(it.card, it.quantity) }
            .sortedForDisplay(sortOrder, { it.card }, { it.quantity }, { prices[it.cardId] })
        BinderUiState(
            isLoading = false,
            binder = binder,
            isMissing = isMissing,
            title = binder?.name ?: "All cards",
            progress = progressOf(cards, scope, prices, rate),
            cards = shown,
            filter = userFilter,
            sort = sortOrder
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BinderUiState())

    fun updateFilter(newFilter: CardFilter) { filter.value = newFilter }

    fun setQuery(query: String) { filter.value = filter.value.copy(query = query) }

    fun setSort(newSort: CardSort) { sort.value = newSort }

    fun setQuantity(item: CardWithQuantity, quantity: Int) {
        if (quantity < 0) return
        viewModelScope.launch { repository.updateCardQuantity(Binder.MAIN_ID, item.card.cardId, quantity) }
    }

    private val allCards = repository.allCards.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** How many cards a binder definition covers, for the editor's preview. */
    fun countFor(binder: Binder): Int {
        val scope = binder.scope()
        return allCards.value.count { scope.matches(it, 0) }
    }

    fun saveBinder(binder: Binder) = repository.saveBinder(binder)

    fun deleteBinder() = repository.deleteBinder(binderId)

    /** A description of the binder's scope for its header, e.g. "Origins · No showcase". */
    fun describe(binder: Binder?): String = binder?.describe(RiftSet.namesByCode) ?: "Every card in every set"

    class Factory(
        private val repository: CardRepository,
        private val priceRepository: PriceRepository,
        private val binderId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BinderViewModel(repository, priceRepository, binderId) as T
    }
}
