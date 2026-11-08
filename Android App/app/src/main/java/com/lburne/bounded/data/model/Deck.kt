package com.lburne.bounded.data.model

import java.util.UUID

data class Deck(
    val deckId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val format: DeckFormat = DeckFormat.CONSTRUCTED, // Updated default
    val legendCardId: String? = null,
    val championCardId: String? = null,
    val coverCardUrl: String? = null,
    val mainCount: Int = 0, // Units, spells and gear in the main deck, including the champion
    val sideCount: Int = 0,
    val lastModified: Long = System.currentTimeMillis()
)