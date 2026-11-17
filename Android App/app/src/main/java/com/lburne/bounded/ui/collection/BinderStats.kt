package com.lburne.bounded.ui.collection

import com.lburne.bounded.data.filter.CardFilter
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Finish
import com.lburne.bounded.data.model.RiftRarity
import com.lburne.bounded.ui.common.RiftSet

/** A card you own and what one copy is worth, in the user's currency. */
data class ValuableCard(val item: CardWithQuantity, val price: Double)

/**
 * A binder's stats: what the progress panel shows, broken down. Every breakdown is "owned of
 * total" within the binder, so the rows add up to the headline. Mirrors lib/stats.ts in the web app.
 */
data class BinderStats(
    val progress: BinderProgress = BinderProgress(),
    /**
     * Units, spells and gear you own three or more copies of, out of all of them in the binder.
     * Counted by card name across printings, as the deck copy limit is.
     */
    val playsets: BinderProgress = BinderProgress(),
    val byRarity: List<Pair<RiftRarity, BinderProgress>> = emptyList(),
    val byType: List<Pair<CardType, BinderProgress>> = emptyList(),
    /** Per set, only when the binder spans more than one. */
    val bySet: List<Pair<String, BinderProgress>> = emptyList(),
    /** Copies in each finish other than standard, for finishes you own any of. */
    val finishes: List<Pair<Finish, Int>> = emptyList(),
    val mostValuable: List<ValuableCard> = emptyList()
)

/** Copies of a playable card that make a full playset (the deck copy limit). */
const val PLAYSET = 3

private val PLAYSET_TYPES = setOf(CardType.UNIT, CardType.SPELL, CardType.GEAR)
private val TYPE_ORDER = listOf(CardType.LEGEND, CardType.UNIT, CardType.SPELL, CardType.GEAR, CardType.RUNE, CardType.BATTLEFIELD)

fun binderStats(
    cards: List<CardWithQuantity>,
    scope: CardFilter,
    prices: Map<String, Double>,
    rate: Double,
    topCount: Int = 5
): BinderStats {
    val inScope = cards.filter { scope.matches(it.card, it.quantity) }
    fun progress(include: (CardWithQuantity) -> Boolean) = progressOf(inScope.filter(include), CardFilter(), prices, rate)

    val copiesByName = inScope
        .filter { !it.card.isToken && it.card.type in PLAYSET_TYPES }
        .groupBy { it.card.name }
        .mapValues { (_, printings) -> printings.sumOf { it.quantity } }
    val playsets = BinderProgress(owned = copiesByName.count { it.value >= PLAYSET }, total = copiesByName.size)

    val sets = inScope.map { it.card.setCode }.distinct().sortedBy { code -> RiftSet.values().indexOfFirst { it.code == code }.let { if (it < 0) Int.MAX_VALUE else it } }

    val finishes = Finish.entries.filter { it != Finish.STANDARD }
        .map { finish -> finish to inScope.sumOf { it.count(finish) } }
        .filter { it.second > 0 }

    val mostValuable = inScope
        .filter { it.quantity > 0 }
        .mapNotNull { item -> prices[item.card.cardId]?.takeIf { it > 0 }?.let { ValuableCard(item, it * rate) } }
        .sortedByDescending { it.price }
        .take(topCount)

    return BinderStats(
        progress = progress { true },
        playsets = playsets,
        byRarity = RiftRarity.values().map { r -> r to progress { it.card.rarity == r } }.filter { it.second.total > 0 },
        byType = TYPE_ORDER.map { t -> t to progress { it.card.type == t } }.filter { it.second.total > 0 },
        bySet = if (sets.size > 1) sets.map { code -> code to progress { it.card.setCode == code } } else emptyList(),
        finishes = finishes,
        mostValuable = mostValuable
    )
}
