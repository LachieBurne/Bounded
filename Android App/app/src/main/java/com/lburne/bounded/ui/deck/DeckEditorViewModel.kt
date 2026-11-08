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
import com.lburne.bounded.data.repository.DeckCards
import com.lburne.bounded.data.repository.PriceRepository
import com.lburne.bounded.data.rules.AddResult
import com.lburne.bounded.data.rules.DeckContents
import com.lburne.bounded.data.rules.DeckCounts
import com.lburne.bounded.data.rules.DeckIssue
import com.lburne.bounded.data.rules.DeckRules
import com.lburne.bounded.data.rules.DeckSection
import com.lburne.bounded.data.rules.DeckStep
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class DeckBuildStep(val step: DeckStep, val title: String, val instruction: String) {
    SELECT_LEGEND(DeckStep.LEGEND, "Select Legend", "Choose 1 Legend card to lead your deck."),
    SELECT_CHAMPION(DeckStep.CHAMPION, "Select Champion", "Choose the Champion unit that matches your Legend."),
    SELECT_RUNES(DeckStep.RUNES, "Select Runes", "Choose 12 Runes from your Legend's domains."),
    SELECT_BATTLEFIELDS(DeckStep.BATTLEFIELDS, "Select Battlefields", "Choose 3 Battlefields with different names."),
    SELECT_MAIN(DeckStep.MAIN, "Build Main Deck", "Add Units, Spells and Gear: at least 40 cards, including your Champion."),
    SELECT_SIDEBOARD(DeckStep.SIDEBOARD, "Sideboard", "Add up to 8 cards (optional)."),
    COMPLETE(DeckStep.COMPLETE, "Deck Complete", "Your deck is ready!");

    companion object {
        fun from(step: DeckStep): DeckBuildStep = values().first { it.step == step }
    }
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

private fun DeckContents.withQuantity(cardId: String, section: DeckSection, quantity: Int): DeckContents {
    fun update(map: Map<String, Int>) = if (quantity > 0) map + (cardId to quantity) else map - cardId
    return if (section == DeckSection.SIDEBOARD) copy(side = update(side)) else copy(main = update(main))
}

private fun DeckContents.quantity(cardId: String, section: DeckSection): Int =
    (if (section == DeckSection.SIDEBOARD) side else main)[cardId] ?: 0

class DeckEditorViewModel(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository,
    private val deckId: String
) : ViewModel() {

    val deck: StateFlow<Deck?> = repository.getDeck(deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val cardsById: StateFlow<Map<String, Card>> = repository.allCards
        .map { list -> list.associateBy { it.cardId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Null until the first real snapshot arrives, so nothing acts on an empty placeholder.
    private val deckCards: StateFlow<DeckCards?> = repository.getDeckCards(deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val mainDeckSlots: StateFlow<List<DeckCardView>> = deckCards.map { it?.main ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sideboardSlots: StateFlow<List<DeckCardView>> = deckCards.map { it?.side ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The deck as the rules see it, or null while it is still loading. */
    private val deckContents: StateFlow<DeckContents?> = combine(deck, deckCards) { d, cards ->
        if (d == null || cards == null) null else DeckContents(
            legendId = d.legendCardId,
            championId = d.championCardId,
            main = cards.main.associate { it.card.cardId to it.deckQuantity },
            side = cards.side.associate { it.card.cardId to it.deckQuantity }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deckCounts: StateFlow<DeckCounts> = combine(deckContents, cardsById) { contents, cards ->
        contents?.let { DeckRules.counts(it, cards) } ?: DeckCounts(0, 0, 0, 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeckCounts(0, 0, 0, 0))

    /** Rules the deck breaks, e.g. after a card's legality changed. Empty when legal or still loading. */
    val deckIssues: StateFlow<List<DeckIssue>> = combine(deckContents, cardsById) { contents, cards ->
        if (contents == null || cards.isEmpty()) emptyList() else DeckRules.validate(contents, cards)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentStep: StateFlow<DeckBuildStep> = combine(deckContents, cardsById) { contents, cards ->
        if (contents == null) DeckBuildStep.SELECT_LEGEND else DeckBuildStep.from(DeckRules.currentStep(contents, cards))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeckBuildStep.SELECT_LEGEND)

    /** Short messages for the screen to show, e.g. why a card couldn't be added. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val cardPrices: StateFlow<Map<String, Double>> = combine(
        priceRepository.prices,
        priceRepository.rate // Re-convert when the currency or exchange rates change
    ) { rawMap, rate ->
        rawMap.mapValues { it.value * rate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Decks saved before mainCount/sideCount existed, or by older app versions, show
        // the wrong count in the deck list. Correct it once when the deck is opened.
        viewModelScope.launch {
            val cards = repository.allCards.first { it.isNotEmpty() }.associateBy { it.cardId }
            val d = repository.getDeck(deckId).first() ?: return@launch
            val current = repository.getDeckCards(deckId).first()
            val contents = DeckContents(
                d.legendCardId, d.championCardId,
                current.main.associate { it.card.cardId to it.deckQuantity },
                current.side.associate { it.card.cardId to it.deckQuantity }
            )
            val counts = DeckRules.counts(contents, cards)
            if (d.mainCount != counts.mainDeck || d.sideCount != counts.sideboard) {
                repository.updateDeckFields(deckId, mapOf("mainCount" to counts.mainDeck, "sideCount" to counts.sideboard))
            }
        }
    }

    fun getExportText(): String {
        val currentDeck = deck.value ?: return ""
        return exportDecklistToText(currentDeck, mainDeckSlots.value, sideboardSlots.value)
    }

    // --- CALCULATE STATS ---
    val deckStats: StateFlow<DeckStats> = combine(
        mainDeckSlots,
        sideboardSlots,
        priceRepository.prices,
        priceRepository.rate
    ) { main, side, prices, rate ->

        val allSlots = main + side

        // Gameplay stats cover the main deck's units, spells and gear.
        val gameplaySlots = main.filter { DeckRules.isPlayable(it.card) }

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

            val cost = slot.card.cost.coerceAtMost(7)
            energyMap[cost] = (energyMap[cost] ?: 0) + qty

            if (slot.card.type == CardType.UNIT) {
                val might = (slot.card.might ?: 0).coerceAtMost(7)
                mightMap[might] = (mightMap[might] ?: 0) + qty
            }

            slot.card.domain.forEach { domain ->
                domainMap[domain] = (domainMap[domain] ?: 0) + qty
            }
        }

        // Prices cover everything, including the sideboard, runes and battlefields.
        var calcTotalValue = 0.0
        var calcMissingValue = 0.0

        allSlots.forEach { slot ->
            val price = (prices[slot.card.cardId] ?: 0.0) * rate
            calcTotalValue += price * slot.deckQuantity
            calcMissingValue += price * slot.missingCount
        }

        DeckStats(
            totalMainDeck = totalMain,
            unitCount = units,
            spellCount = spells,
            gearCount = gear,
            energyCurve = energyMap,
            mightCurve = mightMap,
            domainBreakdown = domainMap,
            totalValue = calcTotalValue,
            ownedValue = calcTotalValue - calcMissingValue,
            missingValue = calcMissingValue
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeckStats())

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

        allCards.filter { item ->
            val card = item.card

            val matchesStepConstraint = DeckRules.isCandidate(card, step.step, legendCard)

            // Hide functional reprints of the base runes (e.g. SFD-R04 duplicates OGN-214) so the
            // picker isn't clogged with identical cards. Alternate arts (UNL-R05a) stay.
            val collectorNumberPart = card.cardId.substringAfter("-")
            val isNotReprint = !collectorNumberPart.startsWith("R", ignoreCase = true) || collectorNumberPart.any { it.isLowerCase() }

            val matchesSearch = if (query.isBlank()) true else {
                card.name.contains(query, ignoreCase = true) || card.type.name.contains(query, ignoreCase = true)
            }

            val matchesDomain = filters.domain == null || card.domain.contains(filters.domain)
            val matchesRarity = filters.rarity == null || card.rarity == filters.rarity
            val matchesType = filters.type == null || card.type == filters.type
            val matchesSet = filters.set == null || card.setCode == filters.set
            val matchesCost = filters.cost == null || card.cost == filters.cost
            val matchesMight = filters.might == null || card.might == filters.might
            val matchesOwned = if (filters.ownedOnly) item.quantity > 0 else true

            matchesStepConstraint && isNotReprint && matchesSearch && matchesDomain && matchesRarity &&
                matchesType && matchesSet && matchesCost && matchesMight && matchesOwned
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

    // --- DECK EDITS ---
    // Edits run one at a time, and each waits until the deck snapshot reflects it before
    // the next starts. Otherwise rapid taps all read the same stale quantity and copies
    // are lost, or go past the copy limit.
    private val editMutex = Mutex()

    private fun edit(block: suspend (deck: Deck, contents: DeckContents, cards: Map<String, Card>) -> Unit) {
        viewModelScope.launch {
            editMutex.withLock {
                val d = deck.value ?: return@withLock
                val contents = deckContents.value ?: return@withLock
                val cards = cardsById.value
                if (cards.isEmpty()) return@withLock
                block(d, contents, cards)
            }
        }
    }

    /** Writes the new quantity with the deck's new counts, then waits for the snapshot to catch up. */
    private suspend fun setQuantity(
        deck: Deck,
        contents: DeckContents,
        cards: Map<String, Card>,
        cardId: String,
        section: DeckSection,
        quantity: Int,
        legendId: String? = contents.legendId,
        championId: String? = contents.championId
    ) {
        val updated = contents.withQuantity(cardId, section, quantity).copy(legendId = legendId, championId = championId)
        val counts = DeckRules.counts(updated, cards)
        val fields = mutableMapOf<String, Any?>("mainCount" to counts.mainDeck, "sideCount" to counts.sideboard)
        if (legendId != contents.legendId) {
            fields["legendCardId"] = legendId
            fields["coverCardUrl"] = legendId?.let { cards[it]?.imageUrl }
        }
        if (championId != contents.championId) fields["championCardId"] = championId
        repository.applyDeckEdit(deck.deckId, cardId, quantity, section == DeckSection.SIDEBOARD, fields)

        // Firestore delivers local writes to listeners almost at once, even offline.
        withTimeoutOrNull(2_000) {
            deckContents.first { c ->
                c != null && c.quantity(cardId, section) == quantity.coerceAtLeast(0) &&
                    c.legendId == legendId && c.championId == championId
            }
        }
    }

    private suspend fun addOne(deck: Deck, contents: DeckContents, cards: Map<String, Card>, cardId: String, section: DeckSection) {
        val result = DeckRules.checkAdd(contents, cardId, section, cards)
        if (result != AddResult.OK) {
            _messages.tryEmit(result.message)
            return
        }
        setQuantity(deck, contents, cards, cardId, section, contents.quantity(cardId, section) + 1)
    }

    private suspend fun removeOne(deck: Deck, contents: DeckContents, cards: Map<String, Card>, cardId: String, section: DeckSection) {
        if (cardId == contents.legendId) return // The legend can't be removed once chosen.
        val current = contents.quantity(cardId, section)
        if (current <= 0) return
        val newQuantity = current - 1
        // Removing the last copy of the champion un-chooses it.
        val championId = if (section == DeckSection.MAIN && cardId == contents.championId && newQuantity == 0) null else contents.championId
        setQuantity(deck, contents, cards, cardId, section, newQuantity, championId = championId)
    }

    /** A tap on a card in the picker: adds it to whatever the current step is building. */
    fun onCardSelect(card: Card) = edit { d, contents, cards ->
        when (DeckRules.currentStep(contents, cards)) {
            DeckStep.LEGEND -> {
                val result = DeckRules.checkAdd(contents, card.cardId, DeckSection.MAIN, cards)
                if (result != AddResult.OK) {
                    _messages.tryEmit(result.message)
                    return@edit
                }
                setQuantity(d, contents, cards, card.cardId, DeckSection.MAIN, contents.quantity(card.cardId, DeckSection.MAIN) + 1, legendId = card.cardId)
            }
            DeckStep.CHAMPION -> {
                val legend = contents.legendId?.let { cards[it] }
                if (!DeckRules.canBeChampion(card, legend)) {
                    _messages.tryEmit("Your champion must be a champion unit that matches your legend.")
                    return@edit
                }
                val result = DeckRules.checkAdd(contents, card.cardId, DeckSection.MAIN, cards)
                if (result != AddResult.OK) {
                    _messages.tryEmit(result.message)
                    return@edit
                }
                setQuantity(d, contents, cards, card.cardId, DeckSection.MAIN, contents.quantity(card.cardId, DeckSection.MAIN) + 1, championId = card.cardId)
            }
            DeckStep.RUNES, DeckStep.BATTLEFIELDS, DeckStep.MAIN ->
                addOne(d, contents, cards, card.cardId, DeckSection.MAIN)
            DeckStep.SIDEBOARD -> addOne(d, contents, cards, card.cardId, DeckSection.SIDEBOARD)
            DeckStep.COMPLETE -> {}
        }
    }

    /** The minus on a card in the picker: removes from the section the current step is building. */
    fun onRemoveCard(cardId: String) = edit { d, contents, cards ->
        val section = if (DeckRules.currentStep(contents, cards) == DeckStep.SIDEBOARD) DeckSection.SIDEBOARD else DeckSection.MAIN
        removeOne(d, contents, cards, cardId, section)
    }

    fun onIncrementCard(cardId: String, isSideboard: Boolean) = edit { d, contents, cards ->
        addOne(d, contents, cards, cardId, if (isSideboard) DeckSection.SIDEBOARD else DeckSection.MAIN)
    }

    fun onDecrementCard(cardId: String, isSideboard: Boolean) = edit { d, contents, cards ->
        removeOne(d, contents, cards, cardId, if (isSideboard) DeckSection.SIDEBOARD else DeckSection.MAIN)
    }

    fun onAddMissingToBinder(cardId: String) {
        viewModelScope.launch {
            val slot = mainDeckSlots.value.find { it.card.cardId == cardId }
                ?: sideboardSlots.value.find { it.card.cardId == cardId }

            if (slot != null) {
                repository.updateCardQuantity("main", cardId, slot.ownedQuantity + 1)
            }
        }
    }

    fun onAddAllMissingToBinder() {
        viewModelScope.launch {
            val allSlots = mainDeckSlots.value + sideboardSlots.value

            // Total deck quantity per card across main deck and sideboard.
            val aggregated = allSlots.groupBy { it.card.cardId }.map { (key, slots) ->
                Triple(key, slots.sumOf { it.deckQuantity }, slots.first().ownedQuantity)
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
