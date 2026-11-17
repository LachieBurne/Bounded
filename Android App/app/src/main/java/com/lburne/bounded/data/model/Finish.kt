package com.lburne.bounded.data.model

/**
 * How a copy of a card is printed. A finish isn't a card of its own: it shares the card's
 * number and art, so the collection counts it on the card's entry (see docs/binders.md).
 * Mirrors lib/finishes.ts in the web app; both run shared/finish-test-cases.json.
 *
 * The order is the order copies are taken away in when only a total changes: standard first,
 * the rarest promo last. [label] is what the apps call it; [promo] finishes carry a PROMO stamp.
 */
enum class Finish(val label: String, val promo: Boolean) {
    STANDARD("Standard", false),
    FOIL("Foil", false),
    NEXUS_NIGHT("Nexus Night", true),
    SKIRMISH_PARTICIPATION("Participation", true),
    SKIRMISH_TOP_8("Top 8", true),
    SKIRMISH_CHAMPION("Champion", true);

    companion object {
        fun parse(name: String): Finish? = entries.firstOrNull { it.name == name }
    }
}

/** How many copies of a card to add (or, when negative, remove), by finish. */
typealias Copies = Map<Finish, Int>

/**
 * The finishes a card comes in: standard; a foil for commons and uncommons (runes have
 * alternate arts instead, which are cards of their own); and the card's promo printings,
 * from data/promos.json.
 */
val Card.finishes: List<Finish>
    get() {
        val hasFoil = !isToken && type != CardType.RUNE && type != CardType.OTHER &&
            (rarity == RiftRarity.COMMON || rarity == RiftRarity.UNCOMMON)
        val promoNames = promos.orEmpty()
        return listOf(Finish.STANDARD) +
            (if (hasFoil) listOf(Finish.FOIL) else emptyList()) +
            Finish.entries.filter { it.promo && it.name in promoNames }
    }
