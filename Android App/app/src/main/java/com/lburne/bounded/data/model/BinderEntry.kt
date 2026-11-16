package com.lburne.bounded.data.model

/**
 * One card in the collection. [quantity] is every copy owned; [foil] and [nexusNight] say how
 * many of those are in that finish, and the rest are standard.
 *
 * Firestore saves every property, so keep helpers here as functions, not `val`s.
 */
data class BinderEntry(
    val binderId: String = "",
    val cardId: String = "",
    val quantity: Int = 0,
    val foil: Int = 0,
    val nexusNight: Int = 0
) {
    /** The finish counts cut down to fit the total, in case something else changed only the total. */
    fun normalised(): BinderEntry {
        val nexus = nexusNight.coerceIn(0, maxOf(0, quantity))
        return copy(nexusNight = nexus, foil = foil.coerceIn(0, maxOf(0, quantity - nexus)))
    }

    /**
     * This entry with [newQuantity] copies in all. New copies are standard ones; copies taken
     * away come off the standard ones first, then foil, then Nexus Night.
     */
    fun withTotal(newQuantity: Int): BinderEntry {
        val current = normalised()
        val nexus = minOf(current.nexusNight, newQuantity)
        return copy(quantity = newQuantity, nexusNight = nexus, foil = minOf(current.foil, newQuantity - nexus))
    }

    /** This entry with [copies] added (or removed, when negative), or null if a finish would go below zero. */
    fun adding(copies: Copies): BinderEntry? {
        val current = normalised()
        val standard = current.quantity - current.foil - current.nexusNight + copies.standard
        val newFoil = current.foil + copies.foil
        val newNexus = current.nexusNight + copies.nexusNight
        if (standard < 0 || newFoil < 0 || newNexus < 0) return null
        return copy(quantity = standard + newFoil + newNexus, foil = newFoil, nexusNight = newNexus)
    }
}
