package com.lburne.bounded.data.local

import androidx.room.Embedded
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.CardType

data class DeckCardView(
    @Embedded val card: Card,
    val deckQuantity: Int,    // How many in this deck?
    val ownedQuantity: Int    // How many in binder?
) {
    // Logic: Base runes (no lowercase letter in collector number) are treated as unlimited/plentiful.
    // We strictly follow the rule: Type is RUNE and ID's collector number does NOT contain any lowercase letters.
    private val isPlentifulRune: Boolean
        get() = card.type == CardType.RUNE && !card.cardId.substringAfter("-").any { it.isLowerCase() }

    // Helper to check if we are missing cards
    val isMissingCards: Boolean
        get() {
            if (isPlentifulRune) return false
            return ownedQuantity < deckQuantity
        }

    val missingCount: Int
        get() {
            if (isPlentifulRune) return 0
            return maxOf(0, deckQuantity - ownedQuantity)
        }
}