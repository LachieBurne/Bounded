package com.lburne.bounded.data.local

import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.Finish

/** A card and how many copies the collection has: [quantity] in all, of which [foil] and [nexusNight] are in that finish. */
data class CardWithQuantity(
    val card: Card,
    val quantity: Int,
    val foil: Int = 0,
    val nexusNight: Int = 0
) {
    fun count(finish: Finish): Int = when (finish) {
        Finish.STANDARD -> quantity - foil - nexusNight
        Finish.FOIL -> foil
        Finish.NEXUS_NIGHT -> nexusNight
    }
}
