package com.lburne.bounded.data.model

/**
 * One card in the collection. [quantity] is every copy owned; [finishes] says how many of those
 * are in each finish other than standard, by [Finish] name (e.g. "FOIL" to 1), and the rest are
 * standard. Keys this build doesn't know are kept, so a newer app's finishes survive an edit
 * here. Rules: docs/binders.md, "Finishes".
 *
 * Read and written by hand in FirebaseUserRepository, not by Firestore's object mapping.
 */
data class BinderEntry(
    val binderId: String = "",
    val cardId: String = "",
    val quantity: Int = 0,
    val finishes: Map<String, Int> = emptyMap()
) {
    /** How many copies of [finish] there are. */
    fun count(finish: Finish): Int =
        if (finish == Finish.STANDARD) quantity - finishes.values.sum() else finishes[finish.name] ?: 0

    /** Zero counts dropped and the finishes cut down to fit the total, the rarest kept first. */
    fun normalised(): BinderEntry {
        val total = maxOf(0, quantity)
        val fitted = mutableMapOf<String, Int>()
        var room = total
        for (kind in keepOrder(finishes.keys)) {
            val take = minOf(maxOf(0, finishes[kind] ?: 0), room)
            if (take > 0) fitted[kind] = take
            room -= take
        }
        return copy(quantity = total, finishes = fitted)
    }

    /**
     * This entry with [newQuantity] copies in all. New copies are standard ones; copies taken
     * away come off the standard ones first, then each finish in [Finish] order.
     */
    fun withTotal(newQuantity: Int): BinderEntry = normalised().copy(quantity = newQuantity).normalised()

    /** This entry with [delta] copies of [finish] added (or removed, when negative), or null if that finish would go below zero. */
    fun adding(finish: Finish, delta: Int): BinderEntry? {
        val current = normalised()
        if (current.count(finish) + delta < 0) return null
        val finishes = current.finishes.toMutableMap()
        if (finish != Finish.STANDARD) {
            val next = (finishes[finish.name] ?: 0) + delta
            if (next > 0) finishes[finish.name] = next else finishes.remove(finish.name)
        }
        return current.copy(quantity = current.quantity + delta, finishes = finishes)
    }

    /** This entry with all of [copies] added, or null if any finish would go below zero. */
    fun adding(copies: Copies): BinderEntry? =
        copies.entries.fold(this as BinderEntry?) { entry, (finish, delta) -> entry?.adding(finish, delta) }

    companion object {
        /**
         * An entry as stored. Builds before promo finishes wrote [foil] and [nexusNight] instead
         * of [finishes]; they're read when [finishes] is missing.
         */
        fun stored(
            binderId: String,
            cardId: String,
            quantity: Int,
            finishes: Map<String, Int>?,
            foil: Int? = null,
            nexusNight: Int? = null
        ): BinderEntry = BinderEntry(
            binderId,
            cardId,
            quantity,
            finishes ?: mapOf(Finish.FOIL.name to (foil ?: 0), Finish.NEXUS_NIGHT.name to (nexusNight ?: 0))
        ).normalised()

        /** Which finishes to keep when the total is too small for them all: kinds this build doesn't know first, then the rarest. */
        private fun keepOrder(kinds: Collection<String>): List<String> {
            val known = Finish.entries.filter { it != Finish.STANDARD }.map { it.name }
            val unknown = kinds.filter { it != Finish.STANDARD.name && it !in known }.sortedDescending()
            return unknown + known.reversed()
        }
    }
}
