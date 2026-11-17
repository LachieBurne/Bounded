package com.lburne.bounded.data.local

import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.Finish

/**
 * A card and how many copies the collection has: [quantity] in all, of which [finishes] are in
 * each finish other than standard, by [Finish] name (see BinderEntry).
 */
data class CardWithQuantity(
    val card: Card,
    val quantity: Int,
    val finishes: Map<String, Int> = emptyMap()
) {
    fun count(finish: Finish): Int =
        if (finish == Finish.STANDARD) quantity - finishes.values.sum() else finishes[finish.name] ?: 0
}
