package com.lburne.bounded.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.filter.CardFilter
import com.lburne.bounded.data.filter.CardSort
import com.lburne.bounded.data.filter.sortedForDisplay
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.Binder
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository
import com.lburne.bounded.ui.common.RiftSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

/**
 * A binder in the tab strip. [setCode] is shown for a single-set binder (a master set) and
 * [domain] for a single-domain one.
 */
data class BinderTab(val binderId: String, val name: String, val setCode: String?, val domain: Domain?)

/** One set's cards when the grid is in set order and spans several sets. */
data class CardSection(val setCode: String, val name: String, val progress: BinderProgress, val cards: List<CardWithQuantity>)

data class CollectionUiState(
    val isLoading: Boolean = true,
    val tabs: List<BinderTab> = emptyList(),
    val selectedId: String = Binder.MAIN_ID,
    /** null for All cards. */
    val binder: Binder? = null,
    val title: String = "All cards",
    val progress: BinderProgress = BinderProgress(),
    /** Progress per domain within the selected binder; null where the binder has none. */
    val domainProgress: Map<Domain, BinderProgress?> = emptyMap(),
    val cards: List<CardWithQuantity> = emptyList(),
    /** Set sections, or empty when the grid isn't split (one set, or another sort). */
    val sections: List<CardSection> = emptyList(),
    val filter: CardFilter = CardFilter(),
    val sort: CardSort = CardSort.SET,
    /** Sets that don't have a master set binder yet, offered when making a binder. */
    val suggestedSets: List<RiftSet> = emptyList()
)

/** The six domains a card can belong to, in the order the sets number them. */
val PlayableDomains = Domain.values().filter { it != Domain.COLORLESS }

/**
 * The Collection tab: All cards by default, with any binders you've made one tap away.
 * Quantities always come from the collection, so a change in one binder shows everywhere.
 */
class CollectionViewModel(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository
) : ViewModel() {

    private val selectedId = MutableStateFlow(Binder.MAIN_ID)
    private val filter = MutableStateFlow(CardFilter())
    private val sort = MutableStateFlow(CardSort.SET)

    private val view = combine(selectedId, filter, sort) { id, f, s -> Triple(id, f, s) }
    private val pricing = combine(priceRepository.prices, priceRepository.rate) { prices, rate -> prices to rate }

    val uiState: StateFlow<CollectionUiState> = combine(
        repository.getBinderCards(Binder.MAIN_ID),
        repository.binders,
        view,
        pricing
    ) { cards, binders, (id, userFilter, sortOrder), (prices, rate) ->
        // A binder deleted elsewhere falls back to All cards.
        val binder = binders.firstOrNull { it.binderId == id }
        val scope = binder?.scope() ?: CardFilter()

        val shown = cards
            .filter { scope.matches(it.card, it.quantity) && userFilter.matches(it.card, it.quantity) }
            .sortedForDisplay(sortOrder, { it.card }, { it.quantity }, { prices[it.cardId] })

        val domainProgress = PlayableDomains.associateWith { domain ->
            if (scope.domains.isNotEmpty() && domain !in scope.domains) null
            else progressOf(cards, scope.copy(domains = setOf(domain)), prices, rate).takeIf { it.total > 0 }
        }

        val sections = if (sortOrder == CardSort.SET) {
            val bySet = shown.groupBy { it.card.setCode }
            if (bySet.size > 1) bySet.map { (code, setCards) ->
                CardSection(code, RiftSet.nameOf(code), progressOf(cards, scope.copy(sets = setOf(code)), prices, rate), setCards)
            } else emptyList()
        } else emptyList()

        val coveredSets = binders.filter { it.sets.size == 1 }.map { it.sets.first() }.toSet()
        CollectionUiState(
            isLoading = false,
            tabs = listOf(BinderTab(Binder.MAIN_ID, "All cards", null, null)) + binders.map { it.toTab() },
            selectedId = binder?.binderId ?: Binder.MAIN_ID,
            binder = binder,
            title = binder?.name ?: "All cards",
            progress = progressOf(cards, scope, prices, rate),
            domainProgress = domainProgress,
            cards = shown,
            sections = sections,
            filter = userFilter,
            sort = sortOrder,
            suggestedSets = RiftSet.values().filter { it.code !in coveredSets }
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CollectionUiState())

    private val allCards: StateFlow<List<Card>> = repository.allCards
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currencySymbol: String get() = priceRepository.getCurrencySymbol()

    fun select(binderId: String) {
        selectedId.value = binderId
        // Filters belong to what you were looking at; start the next binder clean.
        filter.value = CardFilter()
    }

    fun updateFilter(newFilter: CardFilter) { filter.value = newFilter }

    fun setQuery(query: String) { filter.value = filter.value.copy(query = query) }

    fun setSort(newSort: CardSort) { sort.value = newSort }

    /** The quick domain toggles: show just that domain, or everything again. */
    fun toggleDomain(domain: Domain) {
        val current = filter.value
        filter.value = current.copy(domains = if (current.domains == setOf(domain)) emptySet() else setOf(domain))
    }

    fun setQuantity(item: CardWithQuantity, quantity: Int) {
        if (quantity < 0) return
        viewModelScope.launch { repository.updateCardQuantity(Binder.MAIN_ID, item.card.cardId, quantity) }
    }

    /** How many cards a binder definition covers, for the editor's preview. */
    fun countFor(binder: Binder): Int {
        val scope = binder.scope()
        return allCards.value.count { scope.matches(it, 0) }
    }

    /** Saves a binder and shows it. */
    fun saveBinder(binder: Binder) {
        repository.saveBinder(binder)
        select(binder.binderId)
    }

    fun deleteBinder(binderId: String) {
        repository.deleteBinder(binderId)
        if (selectedId.value == binderId) select(Binder.MAIN_ID)
    }

    /** One tap: a binder for every printing in one set. */
    fun createMasterSet(set: RiftSet) =
        saveBinder(Binder(name = "${set.displayName} master set", sets = listOf(set.code)))

    class Factory(private val repository: CardRepository, private val priceRepository: PriceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CollectionViewModel(repository, priceRepository) as T
    }
}

private fun Binder.toTab(): BinderTab {
    val domain = domains.singleOrNull()?.let { name -> Domain.values().firstOrNull { it.name == name } }
    return BinderTab(binderId, name, sets.singleOrNull(), if (sets.isEmpty()) domain else null)
}

fun formatMoney(symbol: String, amount: Double): String = "$symbol${String.format(java.util.Locale.US, "%,.2f", amount)}"
