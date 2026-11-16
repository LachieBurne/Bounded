package com.lburne.bounded.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as the web app's lib/bans.test.ts. */
class CardBanTest {
    private fun card(bannedIn: List<String>?, since: String?) = Card(
        cardId = "TST-001", name = "Test", setCode = "TST", collectorNumber = 1, imageUrl = "",
        domain = emptyList(), rarity = RiftRarity.RARE, type = CardType.SPELL, cost = 1, might = null,
        artistName = null, bannedIn = bannedIn, bannedSince = since
    )

    private val both = card(listOf(Card.BAN_STANDARD, Card.BAN_2V2), "2026-11-23")
    private val twoVTwoOnly = card(listOf(Card.BAN_2V2), "2026-07-24")
    private val legal = card(emptyList(), null)

    @Test fun banStartsOnItsDateNotBefore() {
        assertFalse(both.isBannedIn(Card.BAN_STANDARD, "2026-11-22"))
        assertTrue(both.isBannedIn(Card.BAN_STANDARD, "2026-11-23"))
        assertTrue(both.isBannedIn(Card.BAN_2V2, "2026-12-01"))
    }

    @Test fun onlyListedFormatsAreBanned() {
        assertFalse(twoVTwoOnly.isBannedIn(Card.BAN_STANDARD, "2026-12-01"))
        assertTrue(twoVTwoOnly.isBannedIn(Card.BAN_2V2, "2026-12-01"))
        assertFalse(legal.isBannedIn(Card.BAN_STANDARD, "2026-12-01"))
    }

    @Test fun banWithNoDateHasStarted() {
        assertTrue(card(listOf(Card.BAN_STANDARD), null).isBannedIn(Card.BAN_STANDARD, "2000-01-01"))
    }

    @Test fun announcedBanIsUpcomingUntilItStarts() {
        assertEquals("2026-11-23", both.upcomingBan("2026-11-17"))
        assertNull(both.upcomingBan("2026-11-23"))
        assertNull(legal.upcomingBan("2026-11-17"))
    }
}
