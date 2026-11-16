package com.lburne.bounded.data.model

/**
 * How a copy of a card is printed. A finish isn't a card of its own: it shares the card's
 * number and art, so the collection counts it on the card's entry (see docs/binders.md).
 */
enum class Finish(val label: String) {
    STANDARD("Standard"),
    FOIL("Foil"),
    NEXUS_NIGHT("Nexus Night")
}

/** How many copies of a card to add (or, when negative, remove), by finish. */
data class Copies(val standard: Int = 0, val foil: Int = 0, val nexusNight: Int = 0) {
    val total: Int get() = standard + foil + nexusNight

    companion object {
        fun of(finish: Finish, count: Int): Copies = when (finish) {
            Finish.STANDARD -> Copies(standard = count)
            Finish.FOIL -> Copies(foil = count)
            Finish.NEXUS_NIGHT -> Copies(nexusNight = count)
        }
    }
}

/**
 * The finishes a card comes in. Commons and uncommons have a foil printing, and some have a
 * Nexus Night promo with a stronger foil. Runes have alternate arts instead, which are cards
 * of their own. Until there's a list of the Nexus Night promos (docs/card-backlog.md), every
 * card with a foil offers one.
 */
val Card.finishes: List<Finish>
    get() = if (
        !isToken && type != CardType.RUNE && type != CardType.OTHER &&
        (rarity == RiftRarity.COMMON || rarity == RiftRarity.UNCOMMON)
    ) Finish.values().toList() else listOf(Finish.STANDARD)
