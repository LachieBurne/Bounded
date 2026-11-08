package com.lburne.bounded.data.filter

import com.lburne.bounded.data.model.Binder
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFilterTest {

    private fun card(
        id: String,
        name: String = id,
        set: String = id.substringBefore("-"),
        domain: List<Domain> = listOf(Domain.CALM),
        rarity: RiftRarity = RiftRarity.COMMON,
        type: CardType = CardType.UNIT,
        cost: Int = 2,
        might: Int? = 2,
        tags: List<String> = emptyList(),
        sortOrder: Int = 0
    ) = Card(
        cardId = id, name = name, setCode = set, collectorNumber = 1, imageUrl = "", domain = domain,
        rarity = rarity, type = type, cost = cost, might = might, artistName = "Kudos Productions",
        tags = tags, sortOrder = sortOrder
    )

    private val yi = card("OGN-036", "Master Yi, Honed", tags = listOf("Master Yi", "Ionia"), rarity = RiftRarity.RARE, sortOrder = 2)
    private val yiAlt = card("OGN-036a", "Master Yi, Honed", tags = listOf("Master Yi", "Ionia"), rarity = RiftRarity.SHOWCASE, sortOrder = 3)
    private val brawler = card("SFD-010", "Fury Brawler", domain = listOf(Domain.FURY), cost = 9, might = null, sortOrder = 5)
    private val stance = card("OGN-050", "Twin Stance", domain = listOf(Domain.CALM, Domain.BODY), type = CardType.SPELL, cost = 1, might = null, sortOrder = 1)

    @Test
    fun emptyFilterMatchesEverything() {
        val filter = CardFilter()
        assertTrue(filter.isEmpty)
        listOf(yi, yiAlt, brawler, stance).forEach { assertTrue(filter.matches(it, 0)) }
    }

    @Test
    fun valuesInOneCategoryAreAlternatives() {
        val filter = CardFilter(domains = setOf(Domain.FURY, Domain.BODY))
        assertTrue(filter.matches(brawler, 0))
        assertTrue(filter.matches(stance, 0)) // Calm and Body, so Body matches
        assertFalse(filter.matches(yi, 0))
    }

    @Test
    fun categoriesMustAllMatch() {
        val filter = CardFilter(domains = setOf(Domain.CALM), types = setOf(CardType.SPELL))
        assertTrue(filter.matches(stance, 0))
        assertFalse(filter.matches(yi, 0))
        assertEquals(2, filter.activeCount)
    }

    @Test
    fun theTopCostChipMeansThatOrMore() {
        val filter = CardFilter(costs = setOf(CardFilter.STAT_MAX))
        assertTrue(filter.matches(brawler, 0)) // cost 9
        assertFalse(filter.matches(yi, 0))
    }

    @Test
    fun mightFilterSkipsCardsWithoutMight() {
        val filter = CardFilter(mights = setOf(2))
        assertTrue(filter.matches(yi, 0))
        assertFalse(filter.matches(stance, 0))
    }

    @Test
    fun ownershipSplitsOwnedAndMissing() {
        assertTrue(CardFilter(ownership = Ownership.OWNED).matches(yi, 1))
        assertFalse(CardFilter(ownership = Ownership.OWNED).matches(yi, 0))
        assertTrue(CardFilter(ownership = Ownership.MISSING).matches(yi, 0))
        assertFalse(CardFilter(ownership = Ownership.MISSING).matches(yi, 2))
    }

    @Test
    fun searchMatchesEveryWordAcrossNameTagsArtistAndId() {
        assertTrue(CardFilter.matchesQuery(yi, "yi ionia"))
        assertTrue(CardFilter.matchesQuery(yi, "kudos"))
        assertTrue(CardFilter.matchesQuery(yi, "ogn-03"))
        assertFalse(CardFilter.matchesQuery(yi, "yi noxus"))
        assertTrue(CardFilter.matchesQuery(yi, "   "))
    }

    @Test
    fun sortingFallsBackToSetOrder() {
        val cards = listOf(brawler, yiAlt, yi, stance)
        val bySet = cards.sortedForDisplay(CardSort.SET, { it }, { 0 }, { null })
        assertEquals(listOf(stance, yi, yiAlt, brawler), bySet)

        val prices = mapOf(yiAlt.cardId to 40.0, yi.cardId to 1.0)
        val byPrice = cards.sortedForDisplay(CardSort.PRICE, { it }, { 0 }, { prices[it.cardId] })
        assertEquals(listOf(yiAlt, yi, stance, brawler), byPrice)

        val byName = cards.sortedForDisplay(CardSort.NAME, { it }, { 0 }, { null })
        assertEquals(listOf(brawler, yi, yiAlt, stance), byName)
    }

    @Test
    fun masterSetBinderCoversOneSetWithOrWithoutShowcase() {
        val master = Binder(name = "Origins", sets = listOf("OGN"))
        assertTrue(master.scope().matches(yi, 0))
        assertTrue(master.scope().matches(yiAlt, 0))
        assertFalse(master.scope().matches(brawler, 0))

        val base = master.copy(includeShowcase = false)
        assertTrue(base.scope().matches(yi, 0))
        assertFalse(base.scope().matches(yiAlt, 0))
    }

    @Test
    fun binderIgnoresUnknownStoredValues() {
        val binder = Binder(domains = listOf("CALM", "SHADOW"), rarities = listOf("RARE", "MYTHIC"))
        val scope = binder.scope()
        assertEquals(setOf(Domain.CALM), scope.domains)
        assertEquals(setOf(RiftRarity.RARE), scope.rarities)
    }
}
