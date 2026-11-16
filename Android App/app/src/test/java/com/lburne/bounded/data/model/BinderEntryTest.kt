package com.lburne.bounded.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BinderEntryTest {

    private fun entry(quantity: Int, foil: Int = 0, nexusNight: Int = 0) = BinderEntry("main", "OGN-001", quantity, foil, nexusNight)

    @Test
    fun `new copies from a total change are standard`() {
        assertEquals(entry(5, foil = 1, nexusNight = 1), entry(3, foil = 1, nexusNight = 1).withTotal(5))
    }

    @Test
    fun `lowering the total takes standard copies first, then foil, then Nexus Night`() {
        val start = entry(4, foil = 2, nexusNight = 1)
        assertEquals(entry(3, foil = 2, nexusNight = 1), start.withTotal(3))
        assertEquals(entry(2, foil = 1, nexusNight = 1), start.withTotal(2))
        assertEquals(entry(1, foil = 0, nexusNight = 1), start.withTotal(1))
        assertEquals(entry(0), start.withTotal(0))
    }

    @Test
    fun `adding copies of a finish raises the total with it`() {
        assertEquals(entry(3, foil = 1), entry(2).adding(Copies(foil = 1)))
        assertEquals(entry(4, foil = 1, nexusNight = 1), entry(1).adding(Copies(standard = 1, foil = 1, nexusNight = 1)))
    }

    @Test
    fun `removing a copy of a finish you don't have does nothing`() {
        assertNull(entry(2).adding(Copies(foil = -1)))
        assertNull(entry(2, foil = 2).adding(Copies(standard = -1)))
        assertEquals(entry(1, foil = 1), entry(2, foil = 2).adding(Copies(foil = -1)))
    }

    @Test
    fun `finish counts that exceed the total are cut down to fit`() {
        // An app that only knows the total can leave the finish counts behind.
        assertEquals(entry(2, foil = 1, nexusNight = 1), entry(2, foil = 3, nexusNight = 1).normalised())
        assertEquals(entry(3, foil = 1, nexusNight = 1), entry(2, foil = 3, nexusNight = 1).adding(Copies(standard = 1)))
    }

    @Test
    fun `only commons and uncommons that aren't runes have foil finishes`() {
        fun card(rarity: RiftRarity, type: CardType = CardType.UNIT) = Card(
            cardId = "OGN-001", name = "Test", setCode = "OGN", collectorNumber = 1, imageUrl = "", domain = emptyList(),
            rarity = rarity, type = type, cost = 1, might = 1, artistName = null
        )
        assertEquals(Finish.values().toList(), card(RiftRarity.COMMON).finishes)
        assertEquals(Finish.values().toList(), card(RiftRarity.UNCOMMON, CardType.BATTLEFIELD).finishes)
        assertEquals(listOf(Finish.STANDARD), card(RiftRarity.COMMON, CardType.RUNE).finishes)
        assertEquals(listOf(Finish.STANDARD), card(RiftRarity.RARE).finishes)
        assertEquals(listOf(Finish.STANDARD), card(RiftRarity.SHOWCASE).finishes)
    }
}
