package com.lburne.bounded.ui.binder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Helper class to group filter inputs
private data class FilterCriteria(
    val domain: Domain? = null,
    val rarity: RiftRarity? = null,
    val type: CardType? = null,
    val set: String? = null,
    val cost: Int? = null,
    val might: Int? = null,
    val ownedOnly: Boolean = false
)

// Helper class for visual filters to keep combining clean
private data class VisualFilters(
    val domain: Domain?,
    val rarity: RiftRarity?,
    val type: CardType?,
    val set: String?
)

class BinderViewModel(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository
) : ViewModel() {

    // 1. Inputs
    private val _selectedBinderId = MutableStateFlow("main")
    private val _searchQuery = MutableStateFlow("")
    private val _filterDomain = MutableStateFlow<Domain?>(null)
    private val _filterRarity = MutableStateFlow<RiftRarity?>(null)
    private val _filterType = MutableStateFlow<CardType?>(null)
    private val _filterSet = MutableStateFlow<String?>(null)
    private val _filterCost = MutableStateFlow<Int?>(null)
    private val _filterMight = MutableStateFlow<Int?>(null)
    private val _showOwnedOnly = MutableStateFlow(false)

    // 2. Intermediate Step: Group filters to avoid 5-arg combine limit
    private val _visualFilters = combine(_filterDomain, _filterRarity, _filterType, _filterSet) { d, r, t, s ->
        VisualFilters(d, r, t, s)
    }

    private val _statFilters = combine(_filterCost, _filterMight, _showOwnedOnly) { c, m, o ->
        Triple(c, m, o)
    }

    private val _activeFilters = combine(_visualFilters, _statFilters) { visual, stats ->
        FilterCriteria(visual.domain, visual.rarity, visual.type, visual.set, stats.first, stats.second, stats.third)
    }

    val currentCurrency = priceRepository.currency

    fun setCurrency(code: String) {
        priceRepository.setCurrency(code)
    }

    // 3. Main State Pipeline
    val uiState: StateFlow<BinderUiState> = combine(
        _selectedBinderId,
        _searchQuery,
        _activeFilters
    ) { id, query, filters -> Triple(id, query, filters) }
        .flatMapLatest { (id, query, filters) ->
            // --- STICKY SESSION START ---
            // This block runs whenever the ID, Query, or Filters change.
            // We initialize a "snapshot" of owned IDs here.
            var stickyOwnedIds: Set<String>? = null

            repository.getBinderCards(id).map { rawList ->
                // This runs whenever the database updates (e.g., you press + button)

                // 1. Initialize the snapshot ONLY ONCE for this session.
                //    If we haven't captured the state yet, capture it now.
                if (stickyOwnedIds == null) {
                    stickyOwnedIds = rawList
                        .filter { it.quantity > 0 }
                        .map { it.card.cardId }
                        .toSet()
                }

                // 2. Filter the list
                val filteredList = rawList.filter { item ->
                    // A. Search Match
                    val matchesSearch = if (query.isBlank()) true else {
                        item.card.name.contains(query, ignoreCase = true) ||
                                item.card.artistName?.contains(query, ignoreCase = true) == true
                    }

                    // B. Filter Match
                    val matchesDomain = filters.domain == null || item.card.domain.contains(filters.domain)
                    val matchesRarity = filters.rarity == null || item.card.rarity == filters.rarity
                    val matchesType = filters.type == null || item.card.type == filters.type
                    val matchesSet = filters.set == null || item.card.setCode == filters.set
                    val matchesCost = filters.cost == null || item.card.cost == filters.cost
                    val matchesMight = filters.might == null || item.card.might == filters.might
                    val matchesOwned = if (filters.ownedOnly) item.quantity > 0 else true

                    matchesSearch && matchesDomain && matchesRarity && matchesType && matchesSet && matchesCost && matchesMight && matchesOwned
                }

                // 3. Sort using the STICKY snapshot
                //    Instead of checking "it.quantity > 0" (which changes live),
                //    we check "was it in the owned list when we started?"
                val sortedList = filteredList.sortedWith(
                    compareByDescending<CardWithQuantity> {
                        stickyOwnedIds!!.contains(it.card.cardId)
                    }.thenBy { it.card.cardId }
                )

                BinderUiState(
                    cards = sortedList,
                    currentBinderId = id,
                    isLoading = false,
                    searchQuery = query,
                    filterDomain = filters.domain,
                    filterRarity = filters.rarity,
                    filterType = filters.type,
                    filterSet = filters.set,
                    filterCost = filters.cost,
                    filterMight = filters.might,
                    showOwnedOnly = filters.ownedOnly
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BinderUiState(isLoading = true)
        )

    // --- ACTIONS ---
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun onDomainSelected(domain: Domain?) { _filterDomain.value = domain }
    fun onRaritySelected(rarity: RiftRarity?) { _filterRarity.value = rarity }
    fun onTypeSelected(type: CardType?) { _filterType.value = type }
    fun onSetSelected(set: String?) { _filterSet.value = set }
    fun onCostSelected(cost: Int?) { _filterCost.value = cost }
    fun onMightSelected(might: Int?) { _filterMight.value = might }
    fun onToggleOwnedOnly(show: Boolean) { _showOwnedOnly.value = show }

    fun clearFilters() {
        _searchQuery.value = ""
        _filterDomain.value = null
        _filterRarity.value = null
        _filterType.value = null
        _filterSet.value = null
        _filterCost.value = null
        _filterMight.value = null
        _showOwnedOnly.value = false
    }

    fun onIncrementCard(item: CardWithQuantity) {
        viewModelScope.launch {
            repository.updateCardQuantity("main", item.card.cardId, item.quantity + 1)
        }
    }

    fun onDecrementCard(item: CardWithQuantity) {
        viewModelScope.launch {
            if (item.quantity > 0) {
                repository.updateCardQuantity("main", item.card.cardId, item.quantity - 1)
            }
        }
    }
}