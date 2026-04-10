package com.lburne.bounded.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.local.DeckCardView
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Deck
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DeckBuildStep(val title: String, val instruction: String) {
    SELECT_LEGEND("Select Legend", "Choose 1 Legend card to lead your deck."),
    SELECT_CHAMPION("Select Champion", "Choose the Champion unit that matches your Legend."),
    SELECT_RUNES("Select Runes", "Choose exactly 12 Runes compatible with your Legend."),
    SELECT_BATTLEFIELDS("Select Battlefields", "Choose exactly 3 Battlefields."),
    SELECT_MAIN("Build Main Deck", "Select 39 cards (Units, Spells, etc)."),
    SELECT_SIDEBOARD("Sideboard", "Select up to 8 cards (Optional)."),
    COMPLETE("Deck Complete", "Your deck is ready!")
}

private data class FilterCriteria(
    val domain: Domain? = null,
    val rarity: RiftRarity? = null,
    val type: CardType? = null,
    val set: String? = null,
    val cost: Int? = null,
    val might: Int? = null,
    val ownedOnly: Boolean = false
)

private data class VisualFilters(
    val domain: Domain?,
    val rarity: RiftRarity?,
    val type: CardType?,
    val set: String?
)

data class DeckStats(
    val totalMainDeck: Int = 0,
    val unitCount: Int = 0,
    val spellCount: Int = 0,
    val gearCount: Int = 0,
    val energyCurve: Map<Int, Int> = emptyMap(),
    val mightCurve: Map<Int, Int> = emptyMap(),
    val domainBreakdown: Map<Domain, Int> = emptyMap(),
    val totalValue: Double = 0.0,
    val ownedValue: Double = 0.0,
    val missingValue: Double = 0.0
)

class DeckEditorViewModel(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository,
    private val deckId: String
) : ViewModel() {

    val deck: StateFlow<Deck?> = repository.getDeck(deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cardPrices: StateFlow<Map<String, Double>> = combine(
        priceRepository.prices,       // Source 1: Raw Data
        priceRepository.currency  // Source 2: Trigger (User changed currency)
    ) { rawMap, _ ->
        // Apply convertPrice() to every entry in the map
        rawMap.mapValues { entry ->
            priceRepository.convertPrice(entry.value)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val mainDeckSlots: StateFlow<List<DeckCardView>> = repository.getMainDeckCards(deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sideboardSlots: StateFlow<List<DeckCardView>> = repository.getSideboardCards(deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getExportText(): String {
        val currentDeck = deck.value ?: return ""
        return exportDecklistToText(currentDeck, mainDeckSlots.value, sideboardSlots.value)
    }

    // --- CALCULATE STATS ---
    val deckStats: StateFlow<DeckStats> = combine(
        mainDeckSlots,
        sideboardSlots, // Include Sideboard in stats? Usually yes for price.
        priceRepository.prices
    ) { main, side, prices ->

        // 1. Merge lists for calculations
        val allSlots = main + side

        // 2. Filter for Gameplay Stats (Curves/Counts) - usually Main Deck only
        val gameplaySlots = main.filter {
            it.card.type != CardType.LEGEND &&
                    it.card.type != CardType.RUNE &&
                    it.card.type != CardType.BATTLEFIELD
        }

        val energyMap = mutableMapOf<Int, Int>()
        val mightMap = mutableMapOf<Int, Int>()
        val domainMap = mutableMapOf<Domain, Int>()

        var units = 0
        var spells = 0
        var gear = 0
        var totalMain = 0

        gameplaySlots.forEach { slot ->
            val qty = slot.deckQuantity
            totalMain += qty

            when (slot.card.type) {
                CardType.UNIT -> units += qty
                CardType.SPELL -> spells += qty
                CardType.GEAR -> gear += qty
                else -> {}
            }

            // Energy Curve
            val cost = slot.card.cost.coerceAtMost(7)
            energyMap[cost] = (energyMap[cost] ?: 0) + qty

            // Might Curve
            if (slot.card.type == CardType.UNIT) {
                val might = (slot.card.might ?: 0).coerceAtMost(7)
                mightMap[might] = (mightMap[might] ?: 0) + qty
            }

            // Domain Breakdown
            slot.card.domain.forEach { domain ->
                domainMap[domain] = (domainMap[domain] ?: 0) + qty
            }
        }

        // 3. Price Calculation (Includes Sideboard & Special Cards)
        var calcTotalValue = 0.0
        var calcMissingValue = 0.0

        allSlots.forEach { slot ->
            val price = priceRepository.convertPrice(prices[slot.card.cardId] ?: 0.0)
            val needed = slot.deckQuantity
            val owned = slot.ownedQuantity

            // Total Deck Value
            calcTotalValue += (price * needed)

            // Missing Value (Cost to complete)
            val missingCount = (needed - owned).coerceAtLeast(0)
            calcMissingValue += (price * missingCount)
        }

        val calcOwnedValue = calcTotalValue - calcMissingValue

        DeckStats(
            totalMainDeck = totalMain,
            unitCount = units,
            spellCount = spells,
            gearCount = gear,
            energyCurve = energyMap,
            mightCurve = mightMap,
            domainBreakdown = domainMap,
            totalValue = calcTotalValue,
            ownedValue = calcOwnedValue,
            missingValue = calcMissingValue
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeckStats())

    // --- STEP LOGIC ---
    val currentStep: StateFlow<DeckBuildStep> = combine(deck, mainDeckSlots, sideboardSlots) { deckObj, main, side ->
        if (deckObj == null) return@combine DeckBuildStep.SELECT_LEGEND

        val runes = main.sumOf { if (it.card.type == CardType.RUNE) it.deckQuantity else 0 }
        val battlefields = main.sumOf { if (it.card.type == CardType.BATTLEFIELD) it.deckQuantity else 0 }
        val totalMain = main.sumOf { it.deckQuantity }
        val variableCards = totalMain - 17
        val sideCount = side.sumOf { it.deckQuantity }

        when {
            deckObj.legendCardId == null -> DeckBuildStep.SELECT_LEGEND
            deckObj.championCardId == null -> DeckBuildStep.SELECT_CHAMPION
            runes < 12 -> DeckBuildStep.SELECT_RUNES
            battlefields < 3 -> DeckBuildStep.SELECT_BATTLEFIELDS
            variableCards < 39 -> DeckBuildStep.SELECT_MAIN
            sideCount < 8 -> DeckBuildStep.SELECT_SIDEBOARD
            else -> DeckBuildStep.COMPLETE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeckBuildStep.SELECT_LEGEND)

    // --- FILTER STATES ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filterDomain = MutableStateFlow<Domain?>(null)
    val filterDomain = _filterDomain.asStateFlow()

    private val _filterRarity = MutableStateFlow<RiftRarity?>(null)
    val filterRarity = _filterRarity.asStateFlow()

    private val _filterType = MutableStateFlow<CardType?>(null)
    val filterType = _filterType.asStateFlow()

    private val _filterSet = MutableStateFlow<String?>(null)
    val filterSet = _filterSet.asStateFlow()

    private val _filterCost = MutableStateFlow<Int?>(null)
    val filterCost = _filterCost.asStateFlow()

    private val _filterMight = MutableStateFlow<Int?>(null)
    val filterMight = _filterMight.asStateFlow()

    private val _showOwnedOnly = MutableStateFlow(false)
    val showOwnedOnly = _showOwnedOnly.asStateFlow()

    private val _visualFilters = combine(_filterDomain, _filterRarity, _filterType, _filterSet) { d, r, t, s ->
        VisualFilters(d, r, t, s)
    }
    private val _statFilters = combine(_filterCost, _filterMight, _showOwnedOnly) { c, m, o -> Triple(c, m, o) }

    private val _activeFilters = combine(_visualFilters, _statFilters) { visual, stats ->
        FilterCriteria(visual.domain, visual.rarity, visual.type, visual.set, stats.first, stats.second, stats.third)
    }

    val availableCards: StateFlow<List<CardWithQuantity>> = combine(
        repository.getAllCardsWithOwnership(),
        _searchQuery,
        _activeFilters,
        currentStep,
        deck
    ) { allCards, query, filters, step, deckObj ->

        val legendCard = allCards.find { it.card.cardId == deckObj?.legendCardId }?.card
        val legendDomains = legendCard?.domain ?: emptyList()

        allCards.filter { item ->
            val card = item.card

            val matchesStepConstraint = when (step) {
                DeckBuildStep.SELECT_LEGEND -> card.type == CardType.LEGEND
                DeckBuildStep.SELECT_CHAMPION -> {
                    val legendName = legendCard?.name ?: ""
                    val baseName = legendName.split(" - ").firstOrNull()?.trim() ?: ""
                    baseName.isNotEmpty() && card.name.startsWith(baseName, ignoreCase = true) && card.type == CardType.UNIT
                }
                DeckBuildStep.SELECT_RUNES -> {
                    card.type == CardType.RUNE && legendDomains.containsAll(card.domain)
                }
                DeckBuildStep.SELECT_BATTLEFIELDS -> card.type == CardType.BATTLEFIELD
                DeckBuildStep.SELECT_MAIN, DeckBuildStep.SELECT_SIDEBOARD -> {
                    val isToken = (card.cost == 0 && card.domain.contains(Domain.COLORLESS) && card.rarity == RiftRarity.COMMON)
                    !isToken &&
                            card.type != CardType.BATTLEFIELD &&
                            card.type != CardType.LEGEND &&
                            card.type != CardType.RUNE &&
                            legendDomains.containsAll(card.domain)
                }
                DeckBuildStep.COMPLETE -> false
            }

            // FILTER: Hide all functional reprints from the deck builder (Collector numbers starting with 'R')
            // This prevents the UI from being clogged with identical cards like SFD-R04 and OGN-214
            // Exception: We allow alternate art reprints to pass through (indicated by a lowercase suffix like UNL-R05a)
            val collectorNumberPart = card.cardId.substringAfter("-")
            val isNotReprint = !collectorNumberPart.startsWith("R", ignoreCase = true) || collectorNumberPart.any { it.isLowerCase() }

            val matchesSearch = if (query.isBlank()) true else {
                card.name.contains(query, ignoreCase = true) || card.type.name.contains(query, ignoreCase = true)
            }

            val matchesDomain = filters.domain == null || card.domain.contains(filters.domain)
            val matchesRarity = filters.rarity == null || card.rarity == filters.rarity
            val matchesType = filters.type == null || card.type == filters.type
            val matchesCost = filters.cost == null || card.cost == filters.cost
            val matchesMight = filters.might == null || card.might == filters.might
            val matchesOwned = if (filters.ownedOnly) item.quantity > 0 else true

            matchesStepConstraint && isNotReprint && matchesSearch && matchesDomain && matchesRarity && matchesType && matchesCost && matchesMight && matchesOwned
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun onCardSelect(card: Card) {
        viewModelScope.launch {
            val d = deck.value ?: return@launch
            val step = currentStep.value
            val mainCopies = mainDeckSlots.value.filter { it.card.name == card.name }
            val sideCopies = sideboardSlots.value.filter { it.card.name == card.name }
            val totalCount = mainCopies.sumOf { it.deckQuantity } + sideCopies.sumOf { it.deckQuantity }

            if (step == DeckBuildStep.SELECT_MAIN || step == DeckBuildStep.SELECT_SIDEBOARD) {
                if (totalCount >= 3) return@launch
            }

            val mainQty = mainDeckSlots.value.find { it.card.cardId == card.cardId }?.deckQuantity ?: 0
            val sideQty = sideboardSlots.value.find { it.card.cardId == card.cardId }?.deckQuantity ?: 0

            when (step) {
                DeckBuildStep.SELECT_LEGEND -> {
                    repository.setDeckLegend(d, card)
                    repository.addCardToDeck(d, card.cardId, mainQty)
                }
                DeckBuildStep.SELECT_CHAMPION -> {
                    repository.setDeckChampion(d, card.cardId)
                    repository.addCardToDeck(d, card.cardId, mainQty)
                }
                DeckBuildStep.SELECT_RUNES, DeckBuildStep.SELECT_BATTLEFIELDS -> {
                    repository.addCardToDeck(d, card.cardId, mainQty)
                }
                DeckBuildStep.SELECT_MAIN -> {
                    repository.addCardToDeck(d, card.cardId, mainQty, isSideboard = false)
                }
                DeckBuildStep.SELECT_SIDEBOARD -> {
                    repository.addCardToDeck(d, card.cardId, sideQty, isSideboard = true)
                }
                DeckBuildStep.COMPLETE -> {}
            }
        }
    }

    fun onRemoveCard(cardId: String) {
        viewModelScope.launch {
            val d = deck.value ?: return@launch
            if (d.legendCardId == cardId) return@launch
            val isSideboard = currentStep.value == DeckBuildStep.SELECT_SIDEBOARD
            val qty = if (isSideboard) {
                sideboardSlots.value.find { it.card.cardId == cardId }?.deckQuantity ?: 0
            } else {
                mainDeckSlots.value.find { it.card.cardId == cardId }?.deckQuantity ?: 0
            }
            repository.removeCardFromDeck(d, cardId, qty, isSideboard)
        }
    }

    fun onIncrementCard(cardId: String, isSideboard: Boolean) {
        viewModelScope.launch { 
            val d = deck.value ?: return@launch
            val qty = if (isSideboard) {
                sideboardSlots.value.find { it.card.cardId == cardId }?.deckQuantity ?: 0
            } else {
                mainDeckSlots.value.find { it.card.cardId == cardId }?.deckQuantity ?: 0
            }
            repository.addCardToDeck(d, cardId, qty, isSideboard) 
        }
    }

    fun onDecrementCard(cardId: String, isSideboard: Boolean) {
        viewModelScope.launch {
            val d = deck.value ?: return@launch
            if (!isSideboard && cardId == d.championCardId) {
                val currentCount = mainDeckSlots.value.find { it.card.cardId == cardId }?.deckQuantity ?: 0
                if (currentCount <= 1) repository.setDeckChampion(d, null)
            }
            val qty = if (isSideboard) {
                sideboardSlots.value.find { it.card.cardId == cardId }?.deckQuantity ?: 0
            } else {
                mainDeckSlots.value.find { it.card.cardId == cardId }?.deckQuantity ?: 0
            }
            repository.removeCardFromDeck(d, cardId, qty, isSideboard)
        }
    }

    fun onAddMissingToBinder(cardId: String) {
        viewModelScope.launch {
            val slot = mainDeckSlots.value.find { it.card.cardId == cardId }
                ?: sideboardSlots.value.find { it.card.cardId == cardId }

            if (slot != null) {
                val newQuantity = slot.ownedQuantity + 1
                repository.updateCardQuantity("main", cardId, newQuantity)
            }
        }
    }

    fun onAddAllMissingToBinder() {
        viewModelScope.launch {
            val allSlots = mainDeckSlots.value + sideboardSlots.value
            
            // Group by cardId to get the total deck quantity across main and side
            val aggregated = allSlots.groupBy { it.card.cardId }.map { (key, slots) ->
                val totalDeckQty = slots.sumOf { it.deckQuantity }
                val ownedQty = slots.first().ownedQuantity
                Triple(key, totalDeckQty, ownedQty)
            }

            aggregated.forEach { (cardId, deckQty, ownedQty) ->
                if (ownedQty < deckQty) {
                    repository.updateCardQuantity("main", cardId, deckQty)
                }
            }
        }
    }

    fun getCurrencySymbol(): String {
        return priceRepository.getCurrencySymbol()
    }
}

class DeckEditorViewModelFactory(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository,
    private val deckId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DeckEditorViewModel(repository, priceRepository, deckId) as T
    }
}