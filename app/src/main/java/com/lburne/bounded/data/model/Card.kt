package com.lburne.bounded.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_definitions") // Renamed table
data class Card(
    @PrimaryKey val cardId: String, // e.g., "OGN-001"
    val name: String,
    val setCode: String,
    val collectorNumber: Int,
    val imageUrl: String, // URL or local path
    val domain: List<Domain>,
    val rarity: RiftRarity,
    val type: CardType,
    val cost: Int,
    val might: Int?,
    val artistName: String?
)