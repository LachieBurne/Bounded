package com.lburne.bounded.ui.collection

import com.lburne.bounded.data.filter.CardFilter
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Finish
import com.lburne.bounded.data.model.RiftRarity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as the web app's lib/stats.test.ts. */
class BinderStatsTest {
    private fun card(id: String, rarity: RiftRarity, type: CardType, set: String = id.substringBefore('-')) = Card(
        cardId = id, name = id, setCode = set, collectorNumber = 1, imageUrl = "", domain = emptyList(),
        rarity = rarity, type = type, cost = 1, might = null, artistName = null
    )

    private val cards = listOf(
        CardWithQuantity(card("OGN-001", RiftRarity.COMMON, CardType.UNIT), 3, mapOf(Finish.FOIL.name to 1)),
        CardWithQuantity(card("OGN-002", RiftRarity.COMMON, CardType.SPELL), 1),
        CardWithQuantity(card("OGN-003", RiftRarity.RARE, CardType.UNIT), 0),
        CardWithQuantity(card("SFD-001", RiftRarity.EPIC, CardType.GEAR), 4, mapOf(Finish.NEXUS_NIGHT.name to 2)),
        CardWithQuantity(card("SFD-002", RiftRarity.COMMON, CardType.RUNE), 12)
    )
    private val prices = mapOf("OGN-001" to 0.5, "OGN-003" to 9.0, "SFD-001" to 2.0)

    @Test fun headlineAndPlaysets() {
        val s = binderStats(cards, CardFilter(), prices, rate = 2.0)
        assertEquals(4, s.progress.owned)
        assertEquals(5, s.progress.total)
        assertEquals(20, s.progress.copies)
        assertEquals((0.5 * 3 + 2.0 * 4) * 2.0, s.progress.value, 1e-9)
        // Units, spells and gear only; runes don't count towards playsets.
        assertEquals(BinderProgress(owned = 2, total = 4), s.playsets)
    }

    @Test fun breakdownsAreOwnedOfTotal() {
        val s = binderStats(cards, CardFilter(), prices, rate = 1.0)
        assertEquals(listOf(RiftRarity.COMMON to (3 to 3), RiftRarity.RARE to (0 to 1), RiftRarity.EPIC to (1 to 1)),
            s.byRarity.map { (r, p) -> r to (p.owned to p.total) })
        assertEquals(listOf(CardType.UNIT to (1 to 2), CardType.SPELL to (1 to 1), CardType.GEAR to (1 to 1), CardType.RUNE to (1 to 1)),
            s.byType.map { (t, p) -> t to (p.owned to p.total) })
        assertEquals(listOf("OGN" to (2 to 3), "SFD" to (2 to 2)), s.bySet.map { (c, p) -> c to (p.owned to p.total) })
    }

    @Test fun finishesAndMostValuable() {
        val s = binderStats(cards, CardFilter(), prices, rate = 1.0)
        assertEquals(listOf(Finish.FOIL to 1, Finish.NEXUS_NIGHT to 2), s.finishes)
        // Unowned cards aren't listed, however valuable.
        assertEquals(listOf("SFD-001", "OGN-001"), s.mostValuable.map { it.item.card.cardId })
    }

    @Test fun playsetsCountByNameAcrossPrintings() {
        val showcase = card("OGN-001a", RiftRarity.SHOWCASE, CardType.UNIT, "OGN").copy(name = "OGN-002")
        val s = binderStats(
            listOf(CardWithQuantity(card("OGN-002", RiftRarity.COMMON, CardType.SPELL), 2), CardWithQuantity(showcase, 1)),
            CardFilter(), emptyMap(), rate = 1.0
        )
        assertEquals(BinderProgress(owned = 1, total = 1), s.playsets)
    }

    @Test fun scopedToTheBinder() {
        val s = binderStats(cards, CardFilter(sets = setOf("SFD")), prices, rate = 1.0)
        assertEquals(2 to 2, s.progress.owned to s.progress.total)
        assertEquals(emptyList<Pair<String, BinderProgress>>(), s.bySet) // One set: no per-set rows.
    }
}
