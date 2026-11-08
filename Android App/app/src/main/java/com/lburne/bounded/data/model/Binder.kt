package com.lburne.bounded.data.model

import com.lburne.bounded.data.filter.CardFilter
import java.util.UUID

/**
 * A sub-binder: a saved view of the collection, such as a master set binder for one set.
 * It holds no cards of its own. Quantities always come from the main collection
 * (`binders/main/entries`), so a card is never counted twice.
 *
 * Stored at `users/{uid}/binders/{binderId}`. Enum values are stored by name so an
 * unknown value from a newer app version is ignored instead of failing to load.
 * See docs/binders.md.
 */
data class Binder(
    val binderId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val sets: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val rarities: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    /** Include showcase printings: alt arts, overnumbered and signed cards. */
    val includeShowcase: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** The cards this binder covers, as a filter on the whole card list. */
    fun scope(): CardFilter {
        val chosen = rarities.mapNotNull { enumOrNull<RiftRarity>(it) }.toSet()
        // An empty set means any rarity, so leaving out showcase means listing the rest.
        val rarityFilter = if (includeShowcase) chosen else chosen.ifEmpty { RiftRarity.values().toSet() } - RiftRarity.SHOWCASE
        return CardFilter(
            sets = sets.toSet(),
            domains = domains.mapNotNull { enumOrNull<Domain>(it) }.toSet(),
            rarities = rarityFilter,
            types = types.mapNotNull { enumOrNull<CardType>(it) }.toSet()
        )
    }

    /** A short description of the scope, e.g. "Origins · Fury, Calm · No showcase". */
    fun describe(setNames: Map<String, String>): String {
        val parts = mutableListOf<String>()
        parts += if (sets.isEmpty()) "All sets" else sets.joinToString { setNames[it] ?: it }
        if (domains.isNotEmpty()) parts += domains.joinToString { it.lowercase().replaceFirstChar(Char::titlecase) }
        if (rarities.isNotEmpty()) parts += rarities.joinToString { it.lowercase().replaceFirstChar(Char::titlecase) }
        if (types.isNotEmpty()) parts += types.joinToString { it.lowercase().replaceFirstChar(Char::titlecase) }
        if (!includeShowcase) parts += "No showcase"
        return parts.joinToString(" · ")
    }

    companion object {
        /** The ID of the collection itself, whose entries hold every quantity. */
        const val MAIN_ID = "main"
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
