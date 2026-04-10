package com.lburne.bounded.data.model

data class DeckEntry(
    val deckId: String = "",
    val cardId: String = "",
    val quantity: Int = 0,
    val isSideboard: Boolean = false
)