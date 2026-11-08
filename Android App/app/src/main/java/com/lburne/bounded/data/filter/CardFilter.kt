package com.lburne.bounded.data.filter

import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity

enum class Ownership(val label: String) { ALL("All"), OWNED("Owned"), MISSING("Missing") }

enum class CardSort(val label: String) {
    SET("Set number"),
    NAME("Name"),
    COST("Energy cost"),
    PRICE("Price, highest first"),
    OWNED("Copies owned")
}

/**
 * What the card grids show. Within a category, a card matches if it has any of the selected
 * values (Fury or Calm); across categories, it must match all of them (Fury and Rare).
 * An empty set means "any".
 */
data class CardFilter(
    val query: String = "",
    val sets: Set<String> = emptySet(),
    val domains: Set<Domain> = emptySet(),
    val rarities: Set<RiftRarity> = emptySet(),
    val types: Set<CardType> = emptySet(),
    /** Energy costs. [STAT_MAX] means that value or more. */
    val costs: Set<Int> = emptySet(),
    /** Might. [STAT_MAX] means that value or more. */
    val mights: Set<Int> = emptySet(),
    val ownership: Ownership = Ownership.ALL
) {
    /** How many categories are narrowed (the search box isn't counted). */
    val activeCount: Int
        get() = listOf(sets, domains, rarities, types, costs, mights).count { it.isNotEmpty() } +
            (if (ownership != Ownership.ALL) 1 else 0)

    val isEmpty: Boolean get() = activeCount == 0 && query.isBlank()

    fun matches(card: Card, owned: Int): Boolean {
        if (sets.isNotEmpty() && card.setCode !in sets) return false
        if (domains.isNotEmpty() && card.domain.none { it in domains }) return false
        if (rarities.isNotEmpty() && card.rarity !in rarities) return false
        if (types.isNotEmpty() && card.type !in types) return false
        if (costs.isNotEmpty() && !statMatches(card.cost, costs)) return false
        if (mights.isNotEmpty() && !statMatches(card.might, mights)) return false
        when (ownership) {
            Ownership.ALL -> {}
            Ownership.OWNED -> if (owned <= 0) return false
            Ownership.MISSING -> if (owned > 0) return false
        }
        return matchesQuery(card, query)
    }

    companion object {
        /** The last cost and might chip, which stands for "this or more". */
        const val STAT_MAX = 8

        private fun statMatches(value: Int?, selected: Set<Int>): Boolean {
            if (value == null) return false
            return value in selected || (value >= STAT_MAX && STAT_MAX in selected)
        }

        /**
         * Every word must appear in the name, a tag (champion or region), the artist or the
         * card ID, so "yi ionia" finds Master Yi and "OGN-03" finds OGN-030 to OGN-039.
         */
        fun matchesQuery(card: Card, query: String): Boolean {
            val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return true
            val haystack = buildString {
                append(card.name).append(' ')
                card.tags.forEach { append(it).append(' ') }
                card.artistName?.let { append(it).append(' ') }
                append(card.cardId)
            }
            return words.all { haystack.contains(it, ignoreCase = true) }
        }
    }
}

/** Sorts cards for display. Ties fall back to set order, so the grid never reshuffles. */
fun <T> List<T>.sortedForDisplay(
    sort: CardSort,
    card: (T) -> Card,
    owned: (T) -> Int,
    price: (Card) -> Double?
): List<T> {
    val setOrder = compareBy<T> { card(it).sortOrder }.thenBy { card(it).cardId }
    val comparator = when (sort) {
        CardSort.SET -> setOrder
        CardSort.NAME -> compareBy<T> { card(it).name.lowercase() }.then(setOrder)
        CardSort.COST -> compareBy<T> { card(it).cost }.then(setOrder)
        CardSort.PRICE -> compareByDescending<T> { price(card(it)) ?: -1.0 }.then(setOrder)
        CardSort.OWNED -> compareByDescending<T> { owned(it) }.then(setOrder)
    }
    return sortedWith(comparator)
}
